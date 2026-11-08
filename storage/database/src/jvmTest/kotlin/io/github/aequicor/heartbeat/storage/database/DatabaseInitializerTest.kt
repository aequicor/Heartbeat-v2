package io.github.aequicor.heartbeat.storage.database

import io.github.aequicor.heartbeat.core.database.JvmSqlDriverFactory
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/** The connection must fail before startup initialization and after the owner closes it. */
class DatabaseInitializerTest {

    @Test
    fun connectionFailsBeforeInitializationAndAfterClose() {
        val directory = Files.createTempDirectory("heartbeat-initializer").toString()
        val initializer = DatabaseInitializer(JvmSqlDriverFactory(directory))
        val connection = DefaultDatabaseConnection(initializer)

        assertFailsWith<IllegalStateException> { connection.driver }

        initializer.initialize()
        assertNotNull(connection.driver)

        initializer.close()
        assertFailsWith<IllegalStateException> { connection.driver }
    }

    @Test
    fun initializationIsIdempotent() {
        val directory = Files.createTempDirectory("heartbeat-initializer-idempotent").toString()
        val initializer = DatabaseInitializer(JvmSqlDriverFactory(directory))

        initializer.initialize()
        val first = initializer.requireDriver()
        initializer.initialize()

        assertNotNull(first)
        assertNotNull(initializer.requireDriver())
        initializer.close()
    }
}
