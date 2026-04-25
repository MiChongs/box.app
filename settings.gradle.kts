// settings.gradle.kts
pluginManagement {
    repositories {
        google {
            content {
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
                includeGroupAndSubgroups("androidx")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        val jitpackToken = providers.gradleProperty("authToken").orNull?.takeIf { it.isNotBlank() }

        google()
        mavenCentral()
        maven("https://jitpack.io") {
            if (jitpackToken != null) {
                credentials {
                    username = jitpackToken
                    password = ""
                }
            }
            content {
                includeGroup("com.github.topjohnwu.libsu")
                includeGroup("com.github.getActivity")
            }
        }
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.pkg.github.com/ReChronoRain/HyperCeiler") {
            val ghToken = providers.gradleProperty("hyperceilerToken").orNull?.takeIf { it.isNotBlank() }
            if (ghToken != null) {
                credentials {
                    username = "MiChongs"
                    password = ghToken
                }
            }
            content {
                includeGroup("fan.miuix")
            }
        }
    }
}

rootProject.name = "BoxReApp"
include(":app")
include(":libs:hyperx-compose")
project(":libs:hyperx-compose").projectDir = file("libs/hyperx-compose")
