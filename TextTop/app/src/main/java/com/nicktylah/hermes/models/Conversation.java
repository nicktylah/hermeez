package com.nicktylah.hermes.models;

import java.util.HashMap;

/**
 * Represents an SMS thread in Firebase
 */
public class Conversation {
  private Integer id;
  private Long lastMessageTimestamp;
  private Long lastMessageSender;
  private String lastMessageContent;
  private String lastMessageAttachment;
  private String lastMessageAttachmentContentType;
  private String name;
  private boolean muted;
  private boolean archived;
  private HashMap<String, Boolean> recipients;

  public Conversation() {
    // empty default constructor, necessary for Firebase to be able to deserialize blog posts
  }

  public Conversation(
      Integer id,
      Long lastMessageTimestamp,
      Long lastMessageSender,
      String lastMessageContent,
      String lastMessageAttachment,
      String lastMessageAttachmentContentType,
      String name,
      boolean muted,
      boolean archived,
      HashMap<String, Boolean> recipients
  ) {
    this.id = id;
    this.lastMessageTimestamp = lastMessageTimestamp;
    this.lastMessageSender = lastMessageSender;
    this.lastMessageContent = lastMessageContent;
    this.lastMessageAttachment = lastMessageAttachment;
    this.lastMessageAttachmentContentType = lastMessageAttachmentContentType;
    this.name = name;
    this.muted = muted;
    this.archived = archived;
    this.recipients = recipients;
  }

  public Integer getId() {
    return id;
  }

  public Long getLastMessageTimestamp() {
    return lastMessageTimestamp;
  }

  public Long getLastMessageSender() {
    return lastMessageSender;
  }

  public String getLastMessageContent() {
    return lastMessageContent;
  }

  public String getLastMessageAttachment() {
    return lastMessageAttachment;
  }

  public String getLastMessageAttachmentContentType() {
    return lastMessageAttachmentContentType;
  }

  public String getName() {
    return name;
  }

  public boolean getMuted() {
    return muted;
  }

  public boolean getArchived() {
    return archived;
  }

  public HashMap<String, Boolean> getRecipients() {
    return recipients;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public void setLastMessageTimestamp(Long lastMessageTimestamp) {
    this.lastMessageTimestamp = lastMessageTimestamp;
  }

  public void setLastMessageSender(Long lastMessageSender) {
    this.lastMessageSender = lastMessageSender;
  }

  public void setLastMessageContent(String lastMessageContent) {
    this.lastMessageContent = lastMessageContent;
  }

  public void setLastMessageAttachment(String lastMessageAttachment) {
    this.lastMessageAttachment = lastMessageAttachment;
  }

  public void setLastMessageAttachmentContentType(String lastMessageAttachmentContentType) {
    this.lastMessageAttachmentContentType = lastMessageAttachmentContentType;
  }

  public void setName(String name) {
    this.name = name;
  }

  public void setMuted(boolean muted) {
    this.muted = muted;
  }

  public void setArchived(boolean archived) {
    this.archived = archived;
  }

  public void setRecipients(HashMap<String, Boolean> recipients) {
    this.recipients = recipients;
  }

}
