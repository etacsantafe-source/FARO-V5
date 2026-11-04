package ar.com.faro.v5.control.search;

/**
 * Blocking canonical owner boundary, always used off Android's main thread.
 * The owner must enforce ACL, return only visible records, and read the current
 * active generation. A null stamp is unavailable, never an empty search.
 */
public interface SearchCanonicalReadPort {
    SearchAuthorityStamp authorityStamp();
    SearchCanonicalSnapshot readAuthorized(String namespace, int maxDocuments);
}
