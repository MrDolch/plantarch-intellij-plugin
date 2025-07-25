package com.github.mrdolch.plantarchintellijplugin.diagram.view

import com.charleskorn.kaml.Yaml
import tech.dolch.plantarch.cmd.IdeaRenderJob
import java.awt.event.KeyEvent
import javax.swing.*

fun main() {
  SwingUtilities.invokeLater {
    val frame = JFrame("Diagram Filter Panel Demo")
    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    val jobParams = Yaml.default.decodeFromString(IdeaRenderJob.serializer(), jobParamsYaml)
    frame.contentPane.add(ClassTreePanel(jobParams, { m -> JTree(m) }) {})
    frame.setSize(400, 600)
    frame.setLocationRelativeTo(null)

    frame.rootPane.let { rootPane ->
      rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
        .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "closeWindow")
      rootPane.actionMap.put("closeWindow", object : AbstractAction() {
        override fun actionPerformed(e: java.awt.event.ActionEvent?) {
          frame.dispose()
        }
      })
    }

    frame.isVisible = true
  }
}

var jobParamsYaml = """
projectName: "configurable-google-java-format"
moduleName: "configurable-java-format"
classPaths:
- "/home/chris/IdeaProjects/plantarch-intellij-plugin/build/idea-sandbox/IC-2024.2.5/plugins/plantarch-intellij-plugin/lib/plantarch-0.1.13-SNAPSHOT-launcher.jar"
- "/home/chris/.jdks/temurin-21.0.5!/java.base"
- "/home/chris/.jdks/temurin-21.0.5!/java.compiler"
- "/home/chris/.jdks/temurin-21.0.5!/java.datatransfer"
- "/home/chris/.jdks/temurin-21.0.5!/java.desktop"
- "/home/chris/.jdks/temurin-21.0.5!/java.instrument"
- "/home/chris/.jdks/temurin-21.0.5!/java.logging"
- "/home/chris/.jdks/temurin-21.0.5!/java.management"
- "/home/chris/.jdks/temurin-21.0.5!/java.management.rmi"
- "/home/chris/.jdks/temurin-21.0.5!/java.naming"
- "/home/chris/.jdks/temurin-21.0.5!/java.net.http"
- "/home/chris/.jdks/temurin-21.0.5!/java.prefs"
- "/home/chris/.jdks/temurin-21.0.5!/java.rmi"
- "/home/chris/.jdks/temurin-21.0.5!/java.scripting"
- "/home/chris/.jdks/temurin-21.0.5!/java.se"
- "/home/chris/.jdks/temurin-21.0.5!/java.security.jgss"
- "/home/chris/.jdks/temurin-21.0.5!/java.security.sasl"
- "/home/chris/.jdks/temurin-21.0.5!/java.smartcardio"
- "/home/chris/.jdks/temurin-21.0.5!/java.sql"
- "/home/chris/.jdks/temurin-21.0.5!/java.sql.rowset"
- "/home/chris/.jdks/temurin-21.0.5!/java.transaction.xa"
- "/home/chris/.jdks/temurin-21.0.5!/java.xml"
- "/home/chris/.jdks/temurin-21.0.5!/java.xml.crypto"
- "/home/chris/.jdks/temurin-21.0.5!/jdk.accessibility"
- "/home/chris/.jdks/temurin-21.0.5!/jdk.attach"
- "/home/chris/.jdks/temurin-21.0.5!/jdk.charsets"
- "/home/chris/.jdks/temurin-21.0.5!/jdk.compiler"
- "/home/chris/.jdks/temurin-21.0.5!/jdk.crypto.cryptoki"
- "/home/chris/.jdks/temurin-21.0.5!/jdk.crypto.ec"
- "/home/chris/.jdks/temurin-21.0.5!/jdk.dynalink"
- "/home/chris/.jdks/temurin-21.0.5!/jdk.editpad"
- "/home/chris/.jdks/temurin-21.0.5!/jdk.hotspot.agent"
- "/home/chris/.jdks/temurin-21.0.5!/jdk.httpserver"
- "/home/chris/.jdks/temurin-21.0.5!/jdk.incubator.vector"
- "/home/chris/.jdks/temurin-21.0.5!/jdk.internal.ed"
- "/home/chris/.jdks/temurin-21.0.5!/jdk.internal.jvmstat"
- "/home/chris/.jdks/temurin-21.0.5!/jdk.internal.le"
- "/home/chris/.jdks/temurin-21.0.5!/jdk.internal.opt"
- "/home/chris/.jdks/temurin-21.0.5!/jdk.internal.vm.ci"
- "/home/chris/.jdks/temurin-21.0.5!/jdk.internal.vm.compiler"
- "/home/chris/.jdks/temurin-21.0.5!/jdk.internal.vm.compiler.management"
- "/home/chris/.jdks/temurin-21.0.5!/jdk.jartool"
- "/home/chris/.jdks/temurin-21.0.5!/jdk.javadoc"
- "/home/chris/.jdks/temurin-21.0.5!/jdk.jcmd"
- "/home/chris/.jdks/temurin-21.0.5!/jdk.jconsole"
- "/home/chris/.jdks/temurin-21.0.5!/jdk.jdeps"
- "/home/chris/.jdks/temurin-21.0.5!/jdk.jdi"
- "/home/chris/.jdks/temurin-21.0.5!/jdk.jdwp.agent"
- "/home/chris/.jdks/temurin-21.0.5!/jdk.jfr"
- "/home/chris/.jdks/temurin-21.0.5!/jdk.jlink"
- "/home/chris/.jdks/temurin-21.0.5!/jdk.jpackage"
- "/home/chris/.jdks/temurin-21.0.5!/jdk.jshell"
- "/home/chris/.jdks/temurin-21.0.5!/jdk.jsobject"
- "/home/chris/.jdks/temurin-21.0.5!/jdk.jstatd"
- "/home/chris/.jdks/temurin-21.0.5!/jdk.localedata"
- "/home/chris/.jdks/temurin-21.0.5!/jdk.management"
- "/home/chris/.jdks/temurin-21.0.5!/jdk.management.agent"
- "/home/chris/.jdks/temurin-21.0.5!/jdk.management.jfr"
- "/home/chris/.jdks/temurin-21.0.5!/jdk.naming.dns"
- "/home/chris/.jdks/temurin-21.0.5!/jdk.naming.rmi"
- "/home/chris/.jdks/temurin-21.0.5!/jdk.net"
- "/home/chris/.jdks/temurin-21.0.5!/jdk.nio.mapmode"
- "/home/chris/.jdks/temurin-21.0.5!/jdk.random"
- "/home/chris/.jdks/temurin-21.0.5!/jdk.sctp"
- "/home/chris/.jdks/temurin-21.0.5!/jdk.security.auth"
- "/home/chris/.jdks/temurin-21.0.5!/jdk.security.jgss"
- "/home/chris/.jdks/temurin-21.0.5!/jdk.unsupported"
- "/home/chris/.jdks/temurin-21.0.5!/jdk.unsupported.desktop"
- "/home/chris/.jdks/temurin-21.0.5!/jdk.xml.dom"
- "/home/chris/.jdks/temurin-21.0.5!/jdk.zipfs"
- "/home/chris/IdeaProjects/configurable-google-java-format-maven-plugin/target/classes"
- "/home/chris/.m2/repository/io/github/mrdolch/formatter/configurable-java-format/2025.21.11/configurable-java-format-2025.21.11.jar"
- "/home/chris/.m2/repository/com/google/guava/guava/33.4.0-jre/guava-33.4.0-jre.jar"
- "/home/chris/.m2/repository/com/google/guava/failureaccess/1.0.2/failureaccess-1.0.2.jar"
- "/home/chris/.m2/repository/com/google/guava/listenablefuture/9999.0-empty-to-avoid-conflict-with-guava/listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar"
- "/home/chris/.m2/repository/com/google/code/findbugs/jsr305/3.0.2/jsr305-3.0.2.jar"
- "/home/chris/.m2/repository/org/checkerframework/checker-qual/3.43.0/checker-qual-3.43.0.jar"
- "/home/chris/.m2/repository/com/google/errorprone/error_prone_annotations/2.36.0/error_prone_annotations-2.36.0.jar"
- "/home/chris/.m2/repository/com/google/j2objc/j2objc-annotations/3.0.0/j2objc-annotations-3.0.0.jar"
- "/home/chris/.m2/repository/org/apache/maven/maven-plugin-api/3.9.9/maven-plugin-api-3.9.9.jar"
- "/home/chris/.m2/repository/org/apache/maven/maven-model/3.9.9/maven-model-3.9.9.jar"
- "/home/chris/.m2/repository/org/apache/maven/maven-artifact/3.9.9/maven-artifact-3.9.9.jar"
- "/home/chris/.m2/repository/org/eclipse/sisu/org.eclipse.sisu.plexus/0.9.0.M3/org.eclipse.sisu.plexus-0.9.0.M3.jar"
- "/home/chris/.m2/repository/org/eclipse/sisu/org.eclipse.sisu.inject/0.9.0.M3/org.eclipse.sisu.inject-0.9.0.M3.jar"
- "/home/chris/.m2/repository/org/codehaus/plexus/plexus-component-annotations/2.1.0/plexus-component-annotations-2.1.0.jar"
- "/home/chris/.m2/repository/org/codehaus/plexus/plexus-xml/3.0.0/plexus-xml-3.0.0.jar"
- "/home/chris/.m2/repository/org/codehaus/plexus/plexus-classworlds/2.8.0/plexus-classworlds-2.8.0.jar"
- "/home/chris/.m2/repository/org/apache/maven/plugin-tools/maven-plugin-annotations/3.15.1/maven-plugin-annotations-3.15.1.jar"
- "/home/chris/.m2/repository/org/codehaus/plexus/plexus-utils/4.0.2/plexus-utils-4.0.2.jar"
- "/home/chris/.m2/repository/com/googlecode/java-diff-utils/diffutils/1.3.0/diffutils-1.3.0.jar"
- "/home/chris/IdeaProjects/configurable-google-java-format/core/target/classes"
- "/home/chris/.m2/repository/com/google/guava/guava/33.4.8-jre/guava-33.4.8-jre.jar"
- "/home/chris/.m2/repository/com/google/guava/failureaccess/1.0.3/failureaccess-1.0.3.jar"
- "/home/chris/.m2/repository/org/jspecify/jspecify/1.0.0/jspecify-1.0.0.jar"
- "/home/chris/.m2/repository/com/google/errorprone/error_prone_annotations/2.37.0/error_prone_annotations-2.37.0.jar"
- "/home/chris/.m2/repository/com/google/auto/value/auto-value-annotations/1.11.0/auto-value-annotations-1.11.0.jar"
- "/home/chris/.m2/repository/com/google/auto/service/auto-service-annotations/1.1.1/auto-service-annotations-1.1.1.jar"
renderJob:
  classDiagrams:
    title: "Dependencies of InputOutput"
    description: ""
    classesToAnalyze:
    - "com.google.googlejavaformat.InputOutput"
    - "com.google.googlejavaformat.Newlines"
    containersToHide:
    - "jrt"
    classesToHide:
    - "com.google.googlejavaformat.Input"
    showUseByMethodNames: "NONE"
    projectDir: "/home/chris/IdeaProjects/configurable-google-java-format/core"
    moduleDirs:
    - "/home/chris/IdeaProjects/configurable-google-java-format"
    - "/home/chris/IdeaProjects/configurable-google-java-format-maven-plugin"
    - "/home/chris/IdeaProjects/configurable-google-java-format/core"
optionPanelState:
  targetPumlFile: "/tmp/dependency-diagram-4043177628559265698.puml"
  showPackages: "NESTED"
  classesInFocus:
  - "com.google.googlejavaformat.Input"
  - "com.google.googlejavaformat.InputOutput"
  - "com.google.googlejavaformat.Newlines"
  - "com.google.googlejavaformat.Output"
  - "com.google.googlejavaformat.java.Formatter"
  - "com.google.googlejavaformat.java.ImportOrderer"
  - "com.google.googlejavaformat.java.JavaCommentsHelper"
  - "com.google.googlejavaformat.java.JavaInput"
  - "com.google.googlejavaformat.java.JavaInputAstVisitor"
  - "com.google.googlejavaformat.java.JavaOutput"
  - "com.google.googlejavaformat.java.RemoveUnusedImports"
  - "com.google.googlejavaformat.java.StringWrapper"
  classesInFocusSelected:
  - "com.google.googlejavaformat.InputOutput"
  - "com.google.googlejavaformat.Newlines"
  hiddenContainers:
  - "guava-33.4.0-jre.jar"
  - "jrt"
  hiddenContainersSelected:
  - "jrt"
  hiddenClasses:
  - "com.google.googlejavaformat.Input"
  - "com.google.googlejavaformat.InputOutput"
  - "com.google.googlejavaformat.Newlines"
  - "com.google.googlejavaformat.Output"
  - "com.google.googlejavaformat.java.Formatter"
  - "com.google.googlejavaformat.java.ImportOrderer"
  - "com.google.googlejavaformat.java.JavaCommentsHelper"
  - "com.google.googlejavaformat.java.JavaInput"
  - "com.google.googlejavaformat.java.JavaInputAstVisitor"
  - "com.google.googlejavaformat.java.JavaOutput"
  - "com.google.googlejavaformat.java.RemoveUnusedImports"
  - "com.google.googlejavaformat.java.StringWrapper"
  hiddenClassesSelected:
  - "com.google.googlejavaformat.Input"
"""