package com.transithub.backend.service;

import org.springframework.stereotype.Component;

import javax.naming.NameNotFoundException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.util.Hashtable;
import java.util.Locale;
import java.util.Set;

/**
 * Checks whether an address could plausibly receive mail. This can only rule
 * out bad domains — no DNS lookup can tell you whether a particular mailbox at
 * gmail.com exists, so notreal99@gmail.com still passes. Only an emailed code
 * proves that.
 */
@Component
public class EmailAddressValidator {

    // Throwaway inbox services — signing up with one defeats the point of
    // collecting an address at all.
    private static final Set<String> DISPOSABLE_DOMAINS = Set.of(
            "tempmail.com", "temp-mail.org", "tempmail.net", "10minutemail.com",
            "mailinator.com", "guerrillamail.com", "sharklasers.com", "yopmail.com",
            "throwawaymail.com", "fakeinbox.com", "trashmail.com", "getnada.com",
            "dispostable.com", "maildrop.cc", "mintemail.com", "tempinbox.com",
            "mohmal.com", "emailondeck.com", "spamgourmet.com", "mytemp.email",
            "burnermail.io", "moakt.com", "tmpmail.org", "luxusmail.org"
    );

    public boolean isDisposable(String domain) {
        return DISPOSABLE_DOMAINS.contains(domain.toLowerCase(Locale.ROOT));
    }

    /**
     * True unless DNS says the domain flatly does not exist.
     *
     * An earlier version also required MX (or A) records to be present, which
     * consistently rejected hotmail.com and msn.com — JNDI's DNS provider
     * misreads their MX answers. Turning away real Microsoft users is far
     * worse than letting a fake address through, so existence is now the only
     * test: NXDOMAIN is a rejection, anything else is accepted.
     */
    public boolean domainCanReceiveMail(String domain) {
        Hashtable<String, String> env = new Hashtable<>();
        env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        env.put("com.sun.jndi.dns.timeout.initial", "2000");
        env.put("com.sun.jndi.dns.timeout.retries", "1");

        try {
            DirContext ctx = new InitialDirContext(env);
            try {
                ctx.getAttributes(domain, new String[]{"MX"});
                return true;   // the name resolves — good enough
            } finally {
                ctx.close();
            }
        } catch (NameNotFoundException e) {
            return false;  // definitive: no such domain
        } catch (Exception e) {
            System.err.println("TransitHub: DNS check skipped for " + domain + " – " + e.getMessage());
            return true;   // transient problem — don't punish the user for it
        }
    }
}
