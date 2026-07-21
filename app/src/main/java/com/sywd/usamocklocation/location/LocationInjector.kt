package com.sywd.usamocklocation.location

import com.sywd.usamocklocation.data.StateCapital

interface LocationInjector {
    /** Creates the system test providers. Throws when this app is not the selected mock app. */
    suspend fun start()

    /** Publishes a fresh timestamped location to every active test provider. */
    suspend fun inject(capital: StateCapital)

    /** Removes every test provider created by this injector. Safe to call repeatedly. */
    suspend fun stop()
}
