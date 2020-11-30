// @flow
import * as React from 'react';
import classNames from 'classnames';

import {
  initApp,
  signIn,
  signOut,
  addNotification,
  removeNotification,
  focusSearchBar,
  blurSearchBar,
  searchThreads
} from '../actions/app';
import { sendMessage, isEditingMessage } from '../actions/messages';
import { fetchThreads, selectThread } from '../actions/threads';
// import AppLoader from './appLoader';
import Detail from './detail/detail';
import Notifications from './notifications';
import Search from './search/search';
import Threads from './threads/container';
import Messages from './messages/container';
import AutoSizeInput from './input';

import style from './app.scss';

import type { ElementRef } from 'react';
import type { State as AppState } from '../reducers/app';
import type { State as AuthState } from '../reducers/auth';
import type { State as MessagesState } from '../reducers/messages';
import type { State as NotificationsState } from '../reducers/notifications';
import type { State as SearchState } from '../reducers/search';
import type { State as ThreadsState } from '../reducers/threads';

export type AppProps = {
  children: React.Node,
  location: Location
};

export type ConnectedProps = {
  app: AppState,
  auth: AuthState,
  messages: MessagesState,
  notifications: NotificationsState,
  search: SearchState,
  threads: ThreadsState
};

export type ConnectedDispatch = {
  initApp: typeof initApp,
  signIn: typeof signIn,
  signOut: typeof signOut,
  addNotification: typeof addNotification,
  removeNotification: typeof removeNotification,
  sendMessage: typeof sendMessage,
  isEditingMessage: typeof isEditingMessage,
  focusSearchBar: typeof focusSearchBar,
  blurSearchBar: typeof blurSearchBar,
  searchThreads: typeof searchThreads,
  fetchThreads: typeof fetchThreads,
  selectThread: typeof selectThread
};

type CombinedProps = AppProps & ConnectedProps & ConnectedDispatch;

type State = {
  message: string
};

import { DEFAULT_MESSAGES_LIMIT } from '../../config/constants';

export default class App extends React.Component<CombinedProps, State> {
  lastKeyPressed: string;
  lastKeyPressedTimestamp: number;

  state = {
    message: ''
  };

  componentDidMount() {
    this.props.initApp();

    document.addEventListener('keydown', (e: Object) => {
      const {
        app,
        messages: { editing },
        threads: { threadId, threads },
        focusSearchBar,
        blurSearchBar,
        searchThreads,
        fetchThreads,
        selectThread
      } = this.props;

      switch (e.key) {
        case 'k':
          // cmd + k focuses the search bar
          if (e.metaKey && !app.focusSearchBar) {
            focusSearchBar();
          }
          break;
        case 'Enter':
          if (app.focusSearchBar) {
            blurSearchBar();
            if (e.target.value === '') {
              fetchThreads();
            } else {
              searchThreads(e.target.value);
            }
          }
          break;
        case 'Escape':
          if (app.focusSearchBar) {
            blurSearchBar();
          }
          break;
      }

      // Early abort if we're entering text, otherwise we'd interrupt that weirdly
      // with navigation changes
      if (editing || app.focusSearchBar) {
        return;
      }

      let delta = 0;
      switch (e.key) {
        case 'k':
        case 'j':
          delta = e.key === 'k' ? -1 : 1;
          this.handleThreadNavigation(delta);
          break;
        case 'g':
          // "gg" (within 1s of each other) goes to top of thread list
          if (
            this.lastKeyPressed === 'g' &&
            Date.now() - this.lastKeyPressedTimestamp < 1000
          ) {
            selectThread(threads[0].threadId);
          }
          break;
        case 'G':
          // "G" goes to bottom of thread list
          selectThread(threads[threads.length - 1].threadId);
          break;
      }

      this.lastKeyPressed = e.key;
      this.lastKeyPressedTimestamp = Date.now();
    });
  }

  handleThreadNavigation(delta: number) {
    const { threads: { threadId, threads }, messages } = this.props;
    const currentThreadIndex = threadId
      ? threads.findIndex(t => t.threadId === threadId)
      : -1;
    const currentThreadIdShowingMessages = messages.threadId;

    const newIndex = currentThreadIndex + delta;
    if (threads[newIndex]) {
      this.props.selectThread(threads[newIndex].threadId);
    }
  }

  handleRemoveNotification = (id: number) => {
    this.props.removeNotification(id);
  };

  // TODO: replace with actual flow type
  handleSendMessage = (e: Object) => {
    if (e.key !== 'Enter' || !this.state.message) return;

    // TODO: insert a line break if alt is held down
    // TODO: figure out attachments
    if (!e.altKey) {
      e.preventDefault();
      this.props.sendMessage(
        this.props.threads.contacts.map(c => c.phoneNumber),
        '',
        this.state.message
      );

      this.setState({ message: '' });
    }
  };

  onSignInFailure = (err: Error) => {
    console.error('onSignInFailure', err);
  };

  onSignIn = () => {
    this.props.signIn();
  };

  onSignOut = () => {
    this.props.signOut();
  };

  render() {
    const { props } = this;

    if (!props.app.didInit) {
      return <div />;
      // return <AppLoader />;
    }

    if (!props.app.signedIn) {
      return (
        <div>
          <button
            className={classNames(style.googleSignIn, style.raisedButton)}
            onClick={this.onSignIn}
          >
            <span className={style.iconCont}>
              <img
                className={style.icon}
                alt=""
                src="https://www.gstatic.com/firebasejs/ui/2.0.0/images/auth/google.svg"
              />
            </span>
            <span className={style.textLong}>Sign in with Google</span>
            <span className={style.textShort}>Google</span>
          </button>
        </div>
      );
      // return <Login />;
    }

    return (
      <div className={style.appCont}>
        <Notifications
          notifications={props.notifications}
          onNotificationTimeout={this.handleRemoveNotification}
        />
        <div className={style.topBarCont}>
          <div className={style.searchCont}>
            <Search />
          </div>
          <div className={style.detailsCont}>
            <Detail />
            <div className={style.signOut} onClick={this.onSignOut}>
              Sign Out
            </div>
          </div>
        </div>
        <div className={style.bottomBarCont}>
          <div className={style.leftColumnCont}>
            <Threads />
          </div>
          <div className={style.rightColumnCont}>
            <Messages />
            <AutoSizeInput
              value={this.state.message}
              onChange={message => this.setState({ message })}
              handleSendMessage={this.handleSendMessage}
              isEditingMessage={this.props.isEditingMessage}
            />
          </div>
        </div>
      </div>
    );
  }
}
