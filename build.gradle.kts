import java.util.Properties

plugins {
    id("com.android.application") version "9.2.1" apply false
    id("com.android.library") version "9.2.1" apply false
}

// The release key lives outside the project (default ~/.minimal-launcher-keystore/keystore.properties, or the file named by
// MINIMAL_KEYSTORE_PROPERTIES) and is never committed. The suite apps read it from here; without it a release build is unsigned.
extra["keystoreProps"] = Properties().apply {
    val file = System.getenv("MINIMAL_KEYSTORE_PROPERTIES")?.let { File(it) }
        ?: File(System.getProperty("user.home"), ".minimal-launcher-keystore/keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
