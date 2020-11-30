/**
 * @flow
 *
 * A container for maintaining app level state and actions
 */
import { connect } from 'react-redux';

import App from './app';

import {
  addNotification,
  removeNotification,
  initApp,
  signIn,
  signOut,
  focusSearchBar,
  blurSearchBar,
  searchThreads
} from '../actions/app';
import {
  fetchMessages,
  sendMessage,
  isEditingMessage
} from '../actions/messages';
import { fetchThreads, selectThread } from '../actions/threads';

import type { State } from '../types';

const mapStateToProps = (state: State) => {
  return {
    app: state.app,
    auth: state.auth,
    messages: state.messages,
    notifications: state.notifications,
    search: state.search,
    threads: state.threads
  };
};

export default connect(mapStateToProps, {
  initApp,
  signIn,
  signOut,
  addNotification,
  removeNotification,
  fetchMessages,
  sendMessage,
  isEditingMessage,
  focusSearchBar,
  blurSearchBar,
  searchThreads,
  fetchThreads,
  selectThread
})(App);
