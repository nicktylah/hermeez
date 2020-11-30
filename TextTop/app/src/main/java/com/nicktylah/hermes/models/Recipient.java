package com.nicktylah.hermes.models;

import java.util.HashMap;

/**
 * Represents a Contact in Firebase
 */
public class Recipient {

  private Long id;
  private String name;
  private int countryCode;
  private Long phoneNumber;
  private String photoUri;
  private String color;
  private Long lastUpdated;
  private HashMap<String, Boolean> conversations;

  public Recipient() {
    // empty default constructor, necessary for Firebase to be able to deserialize blog posts
  }

  public Recipient(
      Long id,
      String name,
      int countryCode,
      Long phoneNumber,
      String photoUri,
      String color,
      HashMap<String, Boolean> conversations,
      Long lastUpdated
  ) {
    this.id = id;
    this.name = name;
    this.countryCode = countryCode;
    this.phoneNumber = phoneNumber;
    this.photoUri = photoUri;
    this.color = color;
    this.lastUpdated = lastUpdated;
    this.conversations = conversations;
  }

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public int getCountryCode() {
    return countryCode;
  }


  public Long getPhoneNumber() {
    return phoneNumber;
  }

  public String getPhotoUri() {
    return photoUri;
  }

  public String getColor() {
    return color;
  }

  public Long getLastUpdated() {
    return lastUpdated;
  }

  public HashMap<String, Boolean> getConversations() {
    return conversations;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public void setName(String name) {
     this.name = name;
  }

  public void setCountryCode(int countryCode) {
    this.countryCode = countryCode;
  }

  public void setPhoneNumber(Long phoneNumber) {
    this.phoneNumber = phoneNumber;
  }

  public void setPhotoUri(String photoUri) {
    this.photoUri = photoUri;
  }

  public void setColor(String color) {
    this.color = color;
  }

  public void setLastUpdated(Long lastUpdated) {
    this.lastUpdated = lastUpdated;
  }

  public void setConversations(HashMap<String, Boolean> conversations) {
    this.conversations = conversations;
  }

}
