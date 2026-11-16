package in.railwaydemo;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class PasswordsTest {
    @Test void saltedHashesVerifyWithoutRevealingThePassword() {
        String first=Passwords.hash("correct horse battery staple");
        String second=Passwords.hash("correct horse battery staple");
        assertNotEquals(first,second);
        assertTrue(Passwords.verify("correct horse battery staple",first));
        assertFalse(Passwords.verify("wrong password",first));
        assertFalse(first.contains("correct horse battery staple"));
    }

    @Test void malformedHashFailsClosed() {
        assertFalse(Passwords.verify("anything","not-a-hash"));
    }
}
