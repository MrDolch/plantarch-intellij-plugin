<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# plantarch-intellij-plugin Changelog

## [Unreleased]

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
