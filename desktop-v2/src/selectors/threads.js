// @flow
import { createSelector } from 'reselect';

import type { Thread } from '../types';

// TODO: flatten state into just state.threads, move errors out
const threads = state => state.threads.threads;
const threadId = state => state.threads.threadId;

export const getSortedThreads = createSelector(
  [threads],
  (threads): Thread[] => {
    return threads.sort((a: Thread, b: Thread) => {
      return b.date - a.date;
    });
  }
);

export const getSelectedThreadId = createSelector(
  [threadId],
  (threadId): number => {
    return threadId;
  }
);

export const getSelectedThreadName = createSelector(
  [threads, threadId],
  (threads, threadId): number => {
    const thread = threads.find(t => t.id === threadId);
    return (thread && thread.name) || '';
  }
);
