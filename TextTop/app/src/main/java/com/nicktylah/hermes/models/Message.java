package com.nicktylah.hermes.models;

/**
 * Represents a specific SMS message in Firebase
 */
public class Message {
  private Long id;
  private Long sender;
  private String content;
  private String attachment;
  private String attachmentContentType;
  private Long timestamp;

  public Message() {
    // empty default constructor, necessary for Firebase to be able to deserialize blog posts
  }

  public Message(
      Long id,
      Long sender,
      String content,
      String attachment,
      String attachmentContentType,
      Long timestamp
  ) {
    this.id = id;
    this.sender = sender;
    this.content = content;
    this.attachment = attachment;
    this.attachmentContentType = attachmentContentType;
    this.timestamp = timestamp;
  }

  public Long getId() {
    return id;
  }

  public Long getSender() {
    return sender;
  }

  public String getContent() {
    return content;
  }

  public String getAttachment() {
    return attachment;
  }

  public String getAttachmentContentType() {
    return attachmentContentType;
  }

  public Long getTimestamp() {
    return timestamp;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public void setSender(Long sender) {
    this.sender = sender;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public void setAttachment(String attachment) {
    this.attachment = attachment;
  }

  public void setAttachmentContentType(String attachmentContentType) {
    this.attachmentContentType = attachmentContentType;
  }

  public void setTimestamp(Long timestamp) {
    this.timestamp = timestamp;
  }

}
