// @flow
import { combineReducers } from 'redux';

import app from './app';
import auth from './auth';
import messages from './messages';
import notifications from './notifications';
import search from './search';
import threads from './threads';

const reducers = {
  app,
  auth,
  messages,
  notifications,
  search,
  threads
};

// Generate all the state types based on reducers object
export type Reducers = typeof reducers;

export default combineReducers(reducers);
