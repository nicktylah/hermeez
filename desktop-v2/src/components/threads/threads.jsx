// @flow
import * as React from 'react';
import classnames from 'classnames';

import Thread from './thread';

import { selectThread } from '../../actions/threads';

import { DEFAULT_MESSAGES_LIMIT } from '../../../config/constants';

import type { ElementRef } from 'react';
import type { Thread as TThread } from '../../types';

import style from './threads.scss';

export type ThreadsProps = {|
  children: React.Node,
  location: Location
|};

export type ConnectedProps = {|
  threads: Array<TThread>,
  threadId: number,
  matches: Object[]
|};

export type ConnectedDispatch = {|
  selectThread: typeof selectThread
|};

type CombinedProps = {
  ...ThreadsProps,
  ...ConnectedProps,
  ...ConnectedDispatch
};

export default class Threads extends React.Component<CombinedProps, void> {
  handleSelectThread(threadId: number) {
    if (this.props.threadId === threadId) return;
    this.props.selectThread(threadId);
  }

  render() {
    const { props } = this;

    if (props.threads.error) {
      return <div>Error loading threads.</div>;
    }

    const threads = props.threads.map((t, i) => {
      const threadContStyle = classnames(style.threadCont, {
        [style.selected]: t.threadId === props.threadId
      });

      return (
        <div
          key={i}
          className={threadContStyle}
          onClick={this.handleSelectThread.bind(this, t.threadId)}
        >
          <Thread
            attachment={t.attachment}
            body={t.body}
            contacts={t.contacts}
            date={t.date}
            selected={t.threadId === props.threadId}
            sender={t.sender}
            threadId={t.threadId}
          />
        </div>
      );
    });

    return <div className={style.threadsCont}>{threads}</div>;
  }
}
