import queryString from 'query-string';
import config from '_config';

class EventSourceClient {
  constructor(root) {
    this.root = root;
  }

  subscribe(url, data) {
    let fullUrl = this.root;
    if (url[0] !== '/' && url.length) {
      fullUrl += '/';
    }
    fullUrl += `${url}?${queryString.stringify(data)}`;

    const eventSource = new EventSource(fullUrl);

    eventSource.addEventListener(
      'message',
      e => {
        console.log('lastEventId', e.lastEventId);
        if (e.data === 'ping') {
          console.log(e.data);
          return;
        }
        const messages = JSON.parse(e.data);
        console.log(`Received ${messages.length} messages`);
        messages.slice(messages.length - 5, messages.length).forEach(m => {
          console.log(m);
        });
      },
      false
    );

    eventSource.addEventListener(
      'open',
      e => {
        console.log('opened cnx');
      },
      false
    );

    eventSource.addEventListener(
      'error',
      e => {
        console.log('error!', e, eventSource);
        if (e.readyState == EventSource.CLOSED) {
          console.log('closed cnx');
        }
      },
      false
    );
  }
}

export default new EventSourceClient(config.apiRoot);
