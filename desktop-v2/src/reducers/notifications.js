// @flow
import { ADD_NOTIFICATION, REMOVE_NOTIFICATION } from '../actions/actionTypes';

import type { Action } from '../types';

export type State = Array<{
  id: number,
  message: string,
  type?: 'error' | 'success'
}>;

const initialState = [];

let id = 0;

export default function(state: State = initialState, action: Action) {
  let payload = action.payload || {};

  switch (action.type) {
    case ADD_NOTIFICATION:
      if (!payload) return state;
      return [
        ...state,
        {
          ...payload.notification,
          id: ++id
        }
      ];
    case REMOVE_NOTIFICATION:
      if (!payload.id)
        return state.filter(notification => notification.id !== payload.id);
      return state.filter(notification => notification.id !== payload.id);
    default:
      return state;
  }
}
