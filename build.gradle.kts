// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
}

plugins {
  id("org.sonarqube") version "6.3.1.5724"
}

sonar {
  properties {
    property("sonar.projectKey", "Durodocs_MoneyEye5.0_a0941395-83ff-469d-8ee0-f442d9d41add")
    property("sonar.projectName", "MoneyEye5.0")
  }
}
