package com.example.data.repository

import com.example.domain.model.AuthException
import com.example.domain.model.AuthFailure
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AuthRepositoryImplTest {
    private val firebaseAuth: FirebaseAuth = mockk()
    private lateinit var repository: AuthRepositoryImpl

    @Before
    fun setUp() {
        repository = AuthRepositoryImpl(firebaseAuth)
    }

    private fun mockFirebaseUser(uid: String, email: String): FirebaseUser {
        val user = mockk<FirebaseUser>()
        every { user.uid } returns uid
        every { user.email } returns email
        return user
    }

    @Test
    fun `getCurrentUser maps the persisted Firebase session`() {
        every { firebaseAuth.currentUser } returns mockFirebaseUser("uid1", "test@example.com")

        val user = repository.getCurrentUser()

        assertEquals("uid1", user?.uid)
        assertEquals("test@example.com", user?.email)
    }

    @Test
    fun `getCurrentUser returns null when there is no session`() {
        every { firebaseAuth.currentUser } returns null

        assertNull(repository.getCurrentUser())
    }

    @Test
    fun `login success maps the returned FirebaseUser to a domain User`() = runTest {
        val firebaseUser = mockFirebaseUser("uid1", "test@example.com")
        val authResult = mockk<AuthResult>()
        every { authResult.user } returns firebaseUser
        every { firebaseAuth.signInWithEmailAndPassword("test@example.com", "pw123456") } returns
            Tasks.forResult(authResult)

        val result = repository.login("test@example.com", "pw123456")

        assertTrue(result.isSuccess)
        assertEquals("uid1", result.getOrNull()?.uid)
    }

    // Firebase's leaf exception types (FirebaseAuthInvalidCredentialsException etc.) have no
    // accessible constructor and MockK cannot reliably instantiate them in a plain JVM test, so
    // the `is FirebaseAuthXxxException -> ...` branches in recoverAuthFailure aren't exercised
    // per-subtype here. What *is* testable, and matters most for "no leaking stack traces to the
    // UI", is that *any* unrecognized throwable still comes out the other end as an AuthException
    // rather than propagating raw.
    @Test
    fun `login failure of an unrecognized type is still wrapped as an AuthException`() = runTest {
        every { firebaseAuth.signInWithEmailAndPassword(any(), any()) } returns
            Tasks.forException(RuntimeException("network hiccup"))

        val result = repository.login("test@example.com", "wrongpass")

        assertTrue(result.isFailure)
        val failure = (result.exceptionOrNull() as AuthException).failure
        assertTrue(failure is AuthFailure.Unknown)
    }

    @Test
    fun `signUp failure of an unrecognized type is still wrapped as an AuthException`() = runTest {
        every { firebaseAuth.createUserWithEmailAndPassword(any(), any()) } returns
            Tasks.forException(RuntimeException("collision"))

        val result = repository.signUp("test@example.com", "pw123456")

        assertTrue(result.isFailure)
        val failure = (result.exceptionOrNull() as AuthException).failure
        assertTrue(failure is AuthFailure.Unknown)
    }

    @Test
    fun `signUp success maps the returned FirebaseUser to a domain User`() = runTest {
        val firebaseUser = mockFirebaseUser("uid2", "new@example.com")
        val authResult = mockk<AuthResult>()
        every { authResult.user } returns firebaseUser
        every { firebaseAuth.createUserWithEmailAndPassword("new@example.com", "pw123456") } returns
            Tasks.forResult(authResult)

        val result = repository.signUp("new@example.com", "pw123456")

        assertTrue(result.isSuccess)
        assertEquals("new@example.com", result.getOrNull()?.email)
    }

    @Test
    fun `logout signs out through FirebaseAuth`() {
        every { firebaseAuth.signOut() } returns Unit

        repository.logout()

        verify(exactly = 1) { firebaseAuth.signOut() }
    }

    @Test
    fun `sendPasswordResetEmail success returns Unit`() = runTest {
        every { firebaseAuth.sendPasswordResetEmail("test@example.com") } returns Tasks.forResult(null)

        val result = repository.sendPasswordResetEmail("test@example.com")

        assertTrue(result.isSuccess)
    }

    @Test
    fun `sendPasswordResetEmail failure is wrapped through the same recovery path`() = runTest {
        every { firebaseAuth.sendPasswordResetEmail(any()) } returns
            Tasks.forException(RuntimeException("offline"))

        val result = repository.sendPasswordResetEmail("test@example.com")

        assertTrue(result.isFailure)
        assertTrue((result.exceptionOrNull() as AuthException).failure is AuthFailure.Unknown)
    }
}
