package com.github.mrdolch.plantarchintellijplugin.diagram.command

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import kotlinx.serialization.json.Json
import tech.dolch.plantarch.cmd.RenderJob
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Hält einen PlantArch-Prozess persistent offen und verarbeitet Requests sequentiell. Framing:
 * Alles zwischen zwei "'Ready"-Zeilen ist die Antwort eines Requests.
 */
object PersistentSequentialPlantArchClient : Disposable {

  private val log = Logger.getInstance(PersistentSequentialPlantArchClient::class.java)
  private val json = Json { ignoreUnknownKeys = true }

  // Prozess/Streams
  @Volatile private var handler: OSProcessHandler? = null
  @Volatile private var writer: OutputStreamWriter? = null
  @Volatile private var reader: BufferedReader? = null

  // Lebenszyklus
  private val started = AtomicBoolean(false)
  private val starting = AtomicBoolean(false)
  private val stopping = AtomicBoolean(false)

  // Warteschlange: genau EIN Request aktiv; weitere warten FIFO
  private val sendQueue = LinkedBlockingQueue<Pending>()
  private val inflightQueue: java.util.Queue<Pending> = java.util.concurrent.ConcurrentLinkedQueue()

  private data class Pending(
      val lineToSend: String,
      val result: CompletableFuture<String>,
      val timeoutSec: Long,
  )

  /** Startet den Prozess (lazy), spawnt Reader-Thread, der auf "'Ready" framed. */
  @Synchronized
  fun start(commandLineFactory: () -> GeneralCommandLine) {
    if (started.get() || starting.get()) return
    starting.set(true)
    try {
      val cmd = commandLineFactory()
      val h = OSProcessHandler(cmd)
      handler = h
      writer = OutputStreamWriter(h.processInput, StandardCharsets.UTF_8)
      reader = BufferedReader(InputStreamReader(h.process.inputStream, StandardCharsets.UTF_8))

      // Reader-Thread: framed nach "'Ready"
      thread(start = true, isDaemon = true, name = "PlantArch-stdout-reader") {
        var seenAtLeastOneReady = false
        val sb = StringBuilder()
        try {
          h.startNotify()
          while (true) {
            val line = reader?.readLine() ?: break // EOF
            if (line.startsWith("'Ready")) {
              if (!seenAtLeastOneReady) {
                seenAtLeastOneReady = true
                sb.setLength(0)
              } else {
                val p = inflightQueue.peek()
                if (p == null) {
                  log.debug("Ready with no inflight (probably idle or handshake)")
                } else {
                  deliver(sb.toString())
                }
                sb.setLength(0)
              }
              continue
            } // Normale Nutzlastzeile (PlantUML)
            if (seenAtLeastOneReady) {
              sb.append(line).append('\n')
            } else {
              // Vor dem ersten Ready – ignorieren (oder loggen)
              log.debug("Ignoring pre-handshake line: $line")
            }
          }
        } catch (t: Throwable) {
          log.warn("PlantArch reader aborted", t)
        } finally {
          // Prozess ist beendet -> offene Futures fehlschlagen
          failAllPending(RuntimeException("PlantArch process ended"))
          cleanup()
        }
      }

      started.set(true)
      log.info("PlantArch process started: ${cmd.commandLineString}")
    } catch (t: Throwable) {
      starting.set(false)
      throw t
    } finally {
      starting.set(false)
    }
  }

  /**
   * Führt EINEN Render-Auftrag aus (sequentiell). Rückgabe: PlantUML-Text (vom Server zwischen zwei
   * "'Ready" geliefert).
   */
  fun render(
      job: RenderJob,
      commandLineFactory: () -> GeneralCommandLine,
      timeoutSec: Long = 90,
  ): CompletableFuture<String> {
    ensureStarted(commandLineFactory)

    val reqLine = json.encodeToString(RenderJob.serializer(), job) + "\n"
    val fut = CompletableFuture<String>()
    sendQueue.put(Pending(reqLine, fut, timeoutSec))

    // Sende-Loop in Hintergrund-Thread: holt jeweils Kopf der Queue, schreibt, wartet auf Antwort
    kickSender()

    return fut.orTimeout(timeoutSec, TimeUnit.SECONDS)
  }

  /** Sauber herunterfahren (sendet 'exit'). */
  fun shutdown() {
    if (!started.get() || stopping.getAndSet(true)) return
    try {
      writer?.apply {
        write("exit\n")
        flush()
      }
    } catch (_: Throwable) {
      /* ignore */
    }
    try {
      handler?.destroyProcess()
    } catch (_: Throwable) {
      /* ignore */
    }
    cleanup()
    started.set(false)
    stopping.set(false)
  }

  override fun dispose() = shutdown()

  // --- intern ---

  @Volatile private var senderStarted = false

  @Synchronized
  private fun kickSender() {
    if (senderStarted) return
    senderStarted = true
    thread(start = true, isDaemon = true, name = "PlantArch-stdin-sender") {
      try {
        while (started.get()) {
          val p = sendQueue.take() // 1) aus Sendewarteschlange holen
          try {
            writer?.apply {
              write(p.lineToSend)
              flush()
            }
                ?: run {
                  p.result.completeExceptionally(IllegalStateException("Writer not available"))
                  continue
                }
            inflightQueue.add(p) // 2) jetzt wartet p auf Antwort
          } catch (t: Throwable) {
            p.result.completeExceptionally(t)
          }
        }
      } catch (_: InterruptedException) {
        // shutdown
      } finally {
        senderStarted = false
      }
    }
  }

  private fun deliver(plantUml: String) {
    val pending = inflightQueue.poll()
    if (pending == null) {
      log.warn("Received response but no inflight request\n$plantUml")
      return
    }
    pending.result.complete(plantUml.trimEnd('\n'))
  }

  private fun failAllPending(cause: Throwable) {
    generateSequence { inflightQueue.poll() }.forEach { it.result.completeExceptionally(cause) }
    generateSequence { sendQueue.poll() }.forEach { it.result.completeExceptionally(cause) }
  }

  private fun cleanup() {
    kotlin.runCatching { writer?.close() }
    kotlin.runCatching { reader?.close() }
    writer = null
    reader = null
    handler = null
    started.set(false)
  }

  private fun ensureStarted(factory: () -> GeneralCommandLine) {
    if (!started.get()) start(factory)
  }
}
