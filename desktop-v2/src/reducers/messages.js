// @flow
import {
  ADD_MESSAGES_SUCCESS,
  MESSAGES_OPEN,
  MESSAGES_ERROR,
  GET_MESSAGES_SUCCESS,
  GET_MESSAGES_FAILURE,
  GET_CHECKPOINT_SUCCESS,
  UNSUBSCRIBE_MESSAGES,
  GET_MESSAGES_REQUEST,
  EDITING_MESSAGE,
  SELECT_THREAD
} from '../actions/actionTypes';

import type { Action, Message } from '../types';

export type State = {
  checkpoint: string,
  editing: boolean,
  error: boolean,
  loading: boolean,
  messages: Message[],
  subscribed: boolean,
  // This is the threadId whose messages we're showing, NOT
  // necessarily the currently selected threadId.
  threadId: number
};

const initialState = {
  checkpoint: '0-0',
  editing: false,
  error: false,
  loading: false,
  messages: [],
  subscribed: false,
  threadId: 0
};

export default function(state: State = initialState, action: Action) {
  let payload = action.payload || {};

  switch (action.type) {
    case EDITING_MESSAGE:
      return {
        ...state,
        editing: payload.editing
      };

    case MESSAGES_OPEN:
      return {
        ...state,
        subscribed: true,
        error: false
      };

    case UNSUBSCRIBE_MESSAGES:
      return {
        ...state,
        subscribed: false,
        error: false
      };

    case MESSAGES_ERROR:
      return {
        ...state,
        subscribed: false,
        error: true
      };

    case GET_CHECKPOINT_SUCCESS:
      return {
        ...state,
        checkpoint: payload.checkpoint || state.checkpoint
      };

    // Add new messages to the current display if necessary
    case ADD_MESSAGES_SUCCESS:
      const displayingMessages = state.messages.slice();
      payload.messages
        .filter(message => message.threadId === state.threadId)
        .sort((a: Object, b: Object) => {
          return a.date - b.date;
        })
        .forEach(message => {
          displayingMessages.push(message);
        });

      return {
        ...state,
        messages: handleReactions(displayingMessages),
        checkpoint: payload.checkpoint || state.checkpoint
      };

    case GET_MESSAGES_REQUEST:
      return {
        ...state,
        loading: true
      };

    // Sort by date (we may receive messages out of order) so newest messages are first
    case GET_MESSAGES_SUCCESS:
      // Why the concatenation of messages originally? Leaving the ternary commented for now
      let messages = payload.messages;
      // state.threadId === payload.threadId
      //   ? state.messages.concat(payload.messages)
      //   : payload.messages;
      messages = messages.sort((a: Object, b: Object) => {
        return a.date - b.date;
      });
      messages = handleReactions(messages);

      return {
        ...state,
        messages,
        error: false,
        loading: false,
        threadId: payload.threadId
      };

    case GET_MESSAGES_FAILURE:
      return {
        ...state,
        error: true,
        loading: false
      };
    default:
      return state;
  }
}

const isReactionRegExp = new RegExp(
  /^(Laughed at|Emphasized|Disliked|Liked|Loved|Questioned) “(.+)”$/
);

/**
 * Appends the correct "reactions" (thanks iOS) to messages in the batch.
 * @param messages
 * @returns {Array}
 */
function handleReactions(messages: Message[]): Message[] {
  const messagesToReturn = [];
  messages.forEach(m => {
    const body = m.body || '';
    const isReaction = isReactionRegExp.test(body);
    if (m.type !== 2 && isReaction) {
      const match = body.match(isReactionRegExp);
      if (match && match.length > 1) {
        const originalMessage =
          messagesToReturn.find(m => m.body === match[2]) ||
          messages.find(m => m.body === match[2]);
        if (!originalMessage) {
          messagesToReturn.push(m);
          return;
        }
        const reactions = originalMessage.reactions || [];
        reactions.push({
          type: match[1],
          reactorAddress: m.sender
        });
        originalMessage.reactions = reactions;
      }
    } else {
      messagesToReturn.push(m);
    }
  });

  return messagesToReturn;
}
