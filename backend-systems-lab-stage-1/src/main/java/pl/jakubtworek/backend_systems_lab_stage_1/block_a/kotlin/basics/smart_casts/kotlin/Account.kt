package pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.basics.smart_casts.kotlin

sealed class Account

class RegularUser(
    val name: String
) : Account()

class AdminUser(
    val name: String,
    val permissions: List<String>
) : Account()

class AccountService {

    fun printAccountInfo(account: Account) {
        if (account is AdminUser) {
            // Kotlin automatically smart-casts account to AdminUser.
            // No explicit cast is needed here.
            println(account.permissions)
        }

        if (account is RegularUser) {
            // Kotlin also smart-casts account to RegularUser.
            println(account.name)
        }
    }
}