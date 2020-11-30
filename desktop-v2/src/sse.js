import { debounce } from 'lodash';
import {
  SUBSCRIBE_MESSAGES,
  UNSUBSCRIBE_MESSAGES,
  MESSAGES_OPEN,
  MESSAGES_ERROR,
  MESSAGES_SENT
} from './actions/actionTypes';

const initialDelayMs = 1000;
let eventSource;
let delayMs = initialDelayMs;

export const sse = store => next => action => {
  switch (action.type) {
    // User request to connect
    case SUBSCRIBE_MESSAGES:
      const { payload } = action;
      // Configure the object
      eventSource = new EventSource(payload.url);

      // Attach the callbacks
      eventSource.onopen = () => {
        store.dispatch({ type: MESSAGES_OPEN });
        delayMs = initialDelayMs;
      };
      // TODO: maybe backoff these retries
      eventSource.onerror = event => {
        console.debug('EventSource error', event.target && event.target.error);
        console.debug('EventSource readyState:', eventSource.readyState);
        store.dispatch({ type: MESSAGES_ERROR, payload: event });

        if (eventSource.readyState === 2) {
          // Closed
          cleanupEventSource(eventSource);
          setTimeout(() => {
            store.dispatch({ type: SUBSCRIBE_MESSAGES, payload });
            delayMs += delayMs;
          }, delayMs);
        }
      };
      eventSource.onmessage = event => {
        if (event.data === 'ping') return;

        const messages = JSON.parse(event.data || '[]');
        const lastId = event.lastEventId;

        return store.dispatch({
          type: MESSAGES_SENT,
          payload: {
            messages: messages.map(m => {
              return {
                ...m,
                date: new Date(m.date),
                reactions: []
              };
            }),
            lastId
          }
        });
      };
      break;

    // User request to disconnect
    case UNSUBSCRIBE_MESSAGES:
      eventSource && eventSource.close();
      break;

    default:
      break;
  }

  return next(action);
};

function cleanupEventSource(eventSource) {
  console.debug('cleaning up eventSource', eventSource);
  eventSource.onopen = null;
  eventSource.onerror = null;
  eventSource.onmessage = null;
  console.debug('cleaned up eventSource', eventSource);
}
