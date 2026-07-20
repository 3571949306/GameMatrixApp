package com.gamecenter.app.security

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gamecenter.app.core.security.ModuleSignatureVerifier
import java.io.File
import java.io.RandomAccessFile
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ModuleSignatureVerifierInstrumentedTest {

    @Test
    fun bundledModule_acceptsOfficialSigner_andRejectsTampering() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val moduleFile = File(context.cacheDir, "signature-test-wrongbook.apk")
        context.assets.open("modules/feature_wrongbook_v100.apk").use { input ->
            moduleFile.outputStream().use(input::copyTo)
        }

        assertTrue(
            "official bundled module must pass signer pinning",
            ModuleSignatureVerifier.verify(moduleFile, context) is ModuleSignatureVerifier.Result.Success
        )

        RandomAccessFile(moduleFile, "rw").use { file ->
            val offset = file.length() / 2
            file.seek(offset)
            val original = file.readByte()
            file.seek(offset)
            file.writeByte(original.toInt() xor 0x01)
        }

        assertTrue(
            "tampered module must be rejected",
            ModuleSignatureVerifier.verify(moduleFile, context) is ModuleSignatureVerifier.Result.Failure
        )
    }
}
