// @flow

import {
  GET_THREADS,
  SEARCH_THREADS,
  SELECT_THREAD
} from '../actions/actionTypes';

import type { Action } from '../types';

/**
 * fetchMessages retrieves sms/mms messages from a local IndexedDB
 */
export function fetchThreads(limit?: number): Action {
  return {
    type: GET_THREADS,
    payload: { limit }
  };
}

export function searchThreads(query: string): Action {
  return {
    type: SEARCH_THREADS,
    payload: { query }
  };
}

export function selectThread(threadId: number): Action {
  return {
    type: SELECT_THREAD,
    payload: { threadId }
  };
}
