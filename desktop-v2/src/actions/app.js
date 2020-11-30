// @flow

import {
  AUTH_REQUEST,
  INIT_APP,
  ADD_NOTIFICATION,
  REMOVE_NOTIFICATION,
  SIGN_OUT_REQUEST,
  INIT_FIREBASE,
  FOCUS_SEARCH_BAR,
  BLUR_SEARCH_BAR,
  SEARCH_THREADS
} from './actionTypes';

import type { Action, Notification } from '../types';

/**
 * initApp initializes the app and takes the react router onEnter hook callback.
 * The callback should be called when the app finishes initializing.
 */
export function initApp(): Action {
  return {
    type: INIT_APP,
    payload: {},
    meta: {}
  };
}

export function initFirebase(): Action {
  return {
    type: INIT_FIREBASE,
    payload: {},
    meta: {}
  };
}

export function signIn(): Action {
  return {
    type: AUTH_REQUEST,
    payload: {},
    meta: {}
  };
}

export function signOut(): Action {
  return {
    type: SIGN_OUT_REQUEST,
    payload: {},
    meta: {}
  };
}

export function auth(): Action {
  return {
    type: AUTH_REQUEST,
    payload: {},
    meta: {}
  };
}

export function addNotification(notification: Notification): Action {
  return {
    type: ADD_NOTIFICATION,
    payload: {
      notification
    }
  };
}

export function removeNotification(notificationId: number): Action {
  return {
    type: REMOVE_NOTIFICATION,
    payload: {
      id: notificationId
    }
  };
}

export function focusSearchBar(): Action {
  return {
    type: FOCUS_SEARCH_BAR
  };
}

export function blurSearchBar(): Action {
  return {
    type: BLUR_SEARCH_BAR
  };
}

export function searchThreads(query: string): Action {
  return {
    type: SEARCH_THREADS,
    payload: {
      query
    }
  };
}
