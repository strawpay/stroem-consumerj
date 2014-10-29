package io.stroem.clientj.domain;

import java.util.Date;
import java.util.List;

/**
 * A promissory note.
 */
public class StroemPromissoryNote {
  private final StroemEntity issuer;
  private final Date issued;
  private final long id;
  private final long amount;
  private final String currency;
  private final long validFor; // Valid for # of milliseconds.
  private final List<StroemEntity> requiredLastNegotiations;
  private final List<Long> negotiations;

  public StroemPromissoryNote(StroemEntity issuer, Date issued, long id, long amount, String currency, long validFor, List<StroemEntity> requiredLastNegotiations, List<Long> negotiations) {
    this.issuer = issuer;
    this.issued = issued;
    this.id = id;
    this.amount = amount;
    this.currency = currency;
    this.validFor = validFor;
    this.requiredLastNegotiations = requiredLastNegotiations;
    this.negotiations = negotiations;
  }
}
