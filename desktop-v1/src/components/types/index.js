// @flow

/**
 * Represents an SMS/MMS message
 */
export type Message = {
  attachment: string
  attachmentContentType: string
  content: string
  id: number,
  sender: number,
  timestamp: number,
  inFlight?: boolean
};

/**
 * Represents an SMS/MMS conversation
 */
export type Conversation = {
  id: number,
  lastMessageAttachment: string,
  lastMessageAttachmentContentType: string,
  lastMessageContent: string,
  lastMessageSender: number,
  lastMessageTimestamp: number,
  recipients: {recipientId: boolean},
  recipientInfo?: {recipientId: Recipient}
};

/**
 * Represents a recipient of an SMS/MMS message (a contact)
 */
export type Recipient = {
conversations: {conversationId: boolean},
  countryCode: number,
  id: number,
  name: string,
  phoneNumber: number,
  photoUri: string,
};
