// @flow
export type Action = {
  type: string,
  payload?: *,
  meta?: {}
};

export type ApiAction = {
  type: string,
  payload: *,
  meta: {
    data: *,
    analytics?: Object
  }
};

import type { Reducers } from '../reducers';
export type State = Reducers;

export type IDBDatabase = {
  transaction: Function
};

export type Contact = {
  color: ?string,
  displayName: ?string,
  id: number,
  lastUpdated: ?Date,
  phoneNumber: number,
  photo: ?string,
  photoUri: ?string
};

export type Reaction = {
  type: string,
  reactorAddress: number,
  reactorName?: string
};

export type Message = {
  addresses: string[],
  attachment: ?string,
  attachmentUrl: ?string,
  body: ?string,
  contentType: ?string,
  date: Date,
  reactions: Reaction[],
  sender: number,
  threadId: number,
  type: number
};

export type Thread = {
  attachment: ?string,
  addresses: string[],
  body: ?string,
  contacts: Contact[],
  date: Date,
  sender: number,
  threadId: number
};

export type Notification = {
  message: string,
  type?: string
};
