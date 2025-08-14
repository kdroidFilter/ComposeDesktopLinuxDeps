plugins {
    java
    id("io.github.kdroidfilter.compose.linux.packagedeps")
}

linuxDebConfig {
    debDepends.set(listOf("libqt5widgets5t64"))
}
