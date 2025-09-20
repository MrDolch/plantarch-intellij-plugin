<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# plantarch-intellij-plugin Changelog

## [Unreleased]

## [0.2.3] - 2025-09-20

### Added

* **Marker patterns** (`exact classname`, `Prefix*`, `*Suffix`) with inferred stereotypes in PlantUML.
* **Jar promotion**: auto-include JARs that contain any `classesToAnalyze`.
* **Jump to Project view** for libraries; **Clear Cache** button.
* PlantUML render **error notification**.

### Changed

* **UI rework**: removed ClassTreePanel; interact directly on the diagram (context menus for Show Packages/Methods, Show/Unhide Libraries, Add Class, Focus/Hide/Make Marker).
* **PlantUML generation**: `toPlantUml(deps, params, promotedJarNames)`; smarter container filtering and marker stereotypes from patterns.
* **State/params**: lists → **sets** (`classesToAnalyze`, `classesToHide`, `librariesToHide`, `markerClasses`); new `classesInDiagram`, `librariesDiscovered`. UI labels: *Caption*, *Styles*.

### Removed

* ClassTreePanel and related editor-selection wiring/tests.

### Fixed

* Cleaner edge filtering; safer library handling (no UNKNOWN/unwanted JARs).

### Dev notes

* Update tests to use sets and new `toPlantUml(..., ..., emptySet())`.
* `markerClasses` now `MarkerClasses(setOf(...))`.


## [0.2.2] - 2025-09-18

- Added right-click menus for title, caption, classes, and libraries.
- New toolbar with *Compile*, *Render*, *Auto Render*, and **Show Libraries** option.
- Side menu can now be toggled.
- Improved scrolling and interaction in the diagram view.


## [0.2.1] - 2025-09-15

### Main Changes

- Various bug fixes improving overall stability.
- General code cleanup and refactoring for better maintainability.

### New Features

- Added new context menus for easier and more intuitive usage.
- Introduced "Jump to Source" action, allowing direct navigation from the diagram to the corresponding class in the editor (only for classes inside the source path).
- Enhanced diagram interaction: different context menus depending on whether you click class names or other elements.

## [0.2.0] - 2025-09-14

### Main Changes

- The Class Analyzer has been refactored to improve accuracy and maintainability.
- UML generation has been revised to produce clearer and more consistent diagrams.
- Overall performance has been significantly improved, resulting in faster processing.
- Extensive configuration options have been added to support a wide range of usage scenarios.

### New Features

- Marker classes have been introduced to provide a flexible way of tagging and categorizing elements.
- Stylesheets for markers allow customized visual representations within the generated diagrams.
- A global configuration has been added to centralize and simplify the management of settings across projects.

## [0.1.7] - 2025-07-21

- Render PNGs ourselves
- Remove dependency to PlantUml Plugin

## [0.1.6] - 2025-07-20

- Show Options panel on each diagram
- Remove global Options panel

## [0.1.5] - 2025-06-27

- Fix temp-path-problem on Windows

## [0.1.4] - 2025-06-26

- Switch for show method names
- Switch for show packages
- Show inheritance in context classes too

## [0.1.3] - 2025-06-23

- Fix NPE on analyzing

## [0.1.2] - 2025-06-23

- Enable on Kotlin-Projects
- Enable on Gradle-Projects
- Fix panel init
- Fix too long path
- Fix plugin link

## [0.1.1] - 2025-06-19

- Fix Exceptions on first run
- Add ProgressIndicator
- Replace Lists by Table
- Fix preselection of containers
- Set up workflows

## [0.0.1] - 2025-06-04

- Release of POC

### Added

- Initial scaffold created
  from [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template)
