import { AUTH_SUCCESS, AUTH_FAILURE } from '../actions/actionTypes';

const initialState = false;

export default function auth(state = initialState, action) {
  switch (action.type) {
    case AUTH_SUCCESS:
      return true;
    case AUTH_FAILURE:
      return false;
    default:
      return state;
  }
}
