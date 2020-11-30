// @flow

import {
  GET_MESSAGES,
  SEND_MESSAGE_REQUEST,
  EDITING_MESSAGE
} from '../actions/actionTypes';

import type { Action } from '../types';

/**
 * fetchMessages retrieves sms/mms messages from a local IndexedDB
 */
export function fetchMessages(
  threadId: number,
  limit: number,
  offset: number
): Action {
  return {
    type: GET_MESSAGES,
    payload: { threadId, limit, offset },
    meta: {}
  };
}

/**
 * sendMessage makes a request to the hermeez backend to send a message.
 */
export function sendMessage(
  recipients: number[],
  attachment: ?string,
  body: ?string
): Action {
  return {
    type: SEND_MESSAGE_REQUEST,
    payload: { recipients, attachment, body },
    meta: {}
  };
}

export function isEditingMessage(editing: boolean): Action {
  return {
    type: EDITING_MESSAGE,
    payload: { editing }
  };
}
