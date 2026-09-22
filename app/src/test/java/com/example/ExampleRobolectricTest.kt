package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("DNS Privado Tile", appName)
  }

  @Test
  fun `clean hostname strips protocol prefixes`() {
    assertEquals("adguard.com", DnsHelper.cleanHostname("://adguard.com"))
    assertEquals("dns.adguard.com", DnsHelper.cleanHostname("https://dns.adguard.com/"))
    assertEquals("one.one.one.one", DnsHelper.cleanHostname("tls://one.one.one.one"))
  }

  @Test
  fun `adb command contains correct package and permission`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val cmd = DnsHelper.getAdbCommand(context)
    assert(cmd.contains("android.permission.WRITE_SECURE_SETTINGS"))
    assert(cmd.contains(context.packageName))
  }
}
