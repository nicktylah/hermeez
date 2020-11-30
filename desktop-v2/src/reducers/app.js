/**
 * @flow
 *
 * Application reducer stores global app state e.g. when the app has finished
 * initializing.
 */
import {
  INIT_APP_FINISH,
  SIGN_OUT,
  AUTH_SUCCESS,
  FOCUS_SEARCH_BAR,
  BLUR_SEARCH_BAR
} from '../actions/actionTypes';

import type { Action } from '../types';

export type State = {
  didInit: boolean,
  signedIn: boolean,
  focusSearchBar: boolean
};

const initialState = {
  didInit: false,
  signedIn: false,
  focusSearchBar: false
};

export default function(state: State = initialState, action: Action) {
  let payload = action.payload || {};

  switch (action.type) {
    case INIT_APP_FINISH:
      return {
        ...state,
        didInit: true,
        signedIn: payload.signedIn
      };
    case AUTH_SUCCESS:
      return {
        ...state,
        signedIn: true
      };
    case SIGN_OUT:
      return {
        ...state,
        signedIn: false
      };
    case FOCUS_SEARCH_BAR:
      return {
        ...state,
        focusSearchBar: true
      };
    case BLUR_SEARCH_BAR:
      return {
        ...state,
        focusSearchBar: false
      };
    default:
      return state;
  }
}
