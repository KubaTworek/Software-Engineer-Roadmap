package pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.functional.kotlin

import pl.jakubtworek.backend_systems_lab_stage_1.block_a.kotlin.basics.readability.kotlin.User

// The UserService class shows functional-style programming in Kotlin.
// Kotlin collections have functional operations directly available,
// without explicitly creating a Stream.
class UserService(
    private val users: List<User>
) {

    fun getAdultUserNames(): List<String> {
        // filter keeps only users who match the condition.
        // map transforms User objects into String values.
        return users
            .filter { user -> user.age >= 18 }
            .map { user -> user.fullName }
    }

    fun groupUsersByRole(): Map<String, List<User>> {
        // flatMap creates one role-user pair for each role of each user.
        // groupBy groups the pairs by role.
        // mapValues extracts only the User objects from each group.
        return users
            .flatMap { user ->
                user.roles.map { role -> role to user }
            }
            .groupBy { pair -> pair.first }
            .mapValues { entry ->
                entry.value.map { pair -> pair.second }
            }
    }

    fun hasAnyAdmin(): Boolean {
        // any returns true if at least one user is an admin.
        return users.any { user ->
            "ADMIN" in user.roles
        }
    }
}