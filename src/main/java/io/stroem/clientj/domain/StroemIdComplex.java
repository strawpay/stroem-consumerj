package io.stroem.clientj.domain;


/**
 * Is used to identify a payment channel. In this class anything can be the server Id.
 *
 * If your application is exposing payment channels to some  API,
 * you might want to add caller UID to be able to separate the channels
 * (to avoid applications opening channels that were created by others).
 */
public class StroemIdComplex extends StroemId {

  /**
   * @param serverId - You can use anything as id
   */
  public StroemIdComplex(String serverId) {
    this.serverId = serverId;
  }

}
