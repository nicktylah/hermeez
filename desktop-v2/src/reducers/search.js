// @flow
import Fuse from 'fuse.js';
import {
  ADD_MESSAGES_SUCCESS,
  GET_MESSAGES,
  GET_THREADS_SUCCESS,
  SEARCH_THREADS
} from '../actions/actionTypes';

import type { Action, Contact, Thread } from '../types';

export type State = {
  fuse: ?Object,
  matches: Object[],
  searches: string[]
};

const initialState = {
  fuse: null,
  matches: [],
  searches: []
};

const options = {
  includeMatches: true,
  shouldSort: false, // Use the default chronological ordering
  threshold: 0.3,
  location: 0,
  distance: 100,
  maxPatternLength: 12,
  minMatchCharLength: 3,
  keys: ['contacts.phoneNumber', 'contacts.displayName', 'name']
};

export default function(state: State = initialState, action: Action) {
  let payload = action.payload || {};

  switch (action.type) {
    case GET_THREADS_SUCCESS:
      const fuse = new Fuse(payload.threads, options);
      return {
        ...state,
        fuse
      };

    case SEARCH_THREADS:
      let searches = [...state.searches];
      if (payload.query === '') {
        searches = [];
      } else {
        searches.push(payload.query);
      }
      return {
        ...state,
        searches
      };

    // case GET_MESSAGES:
    //   const selectedThread = state.threads.find(
    //     t => t.threadId === payload.threadId
    //   );
    //   let contacts = [];
    //   let contactsByPhoneNumber = {};
    //   if (selectedThread) {
    //     contacts = selectedThread.contacts;
    //     contacts.forEach(c => (contactsByPhoneNumber[c.phoneNumber] = c));
    //   }
    //   return {
    //     ...state,
    //     contacts,
    //     contactsByPhoneNumber,
    //     threadId: payload.threadId
    //   };

    // // Update the displaying threads on new message arrival
    // case ADD_MESSAGES_SUCCESS:
    //   const threads = state.threads.slice();
    //   payload.messages.forEach(message => {
    //     const thread = threads.find(
    //       thread => thread.threadId === message.threadId
    //     );
    //     if (thread && message.date > thread.date) {
    //       thread.body = message.body;
    //       thread.date = message.date;
    //       thread.sender = message.sender;
    //     }
    //   });
    //   return {
    //     ...state,
    //     threads
    //   };

    default:
      return state;
  }
}
