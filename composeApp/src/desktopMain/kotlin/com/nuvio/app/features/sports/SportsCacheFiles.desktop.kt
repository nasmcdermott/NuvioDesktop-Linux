package com.nuvio.app.features.sports

import com.nuvio.app.core.storage.DesktopStorage
import java.nio.file.Path

internal actual object SportsCacheFiles {
    actual fun dir(): Path = DesktopStorage.accountDir.resolve("sports-cache")
}
