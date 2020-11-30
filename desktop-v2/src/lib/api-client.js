import queryString from 'query-string';
import config from '_config';

class ApiClient {
  constructor(root) {
    this.root = root;
  }

  request(method, url, data) {
    const root = this.root;
    data = data || {};

    let fullUrl = root;
    if (url[0] !== '/' && url.length) {
      fullUrl += '/';
    }
    fullUrl += url;

    const opts = {
      method,
      mode: 'cors'
    };

    if (method === 'GET' || method === 'DELETE') {
      fullUrl += `?${queryString.stringify(data)}`;
    } else {
      opts.body = JSON.stringify(data);
      opts.headers = new Headers({
        Accept: 'application/json',
        'Content-Type': 'application/json'
      });
    }

    return fetch(fullUrl, opts)
      .then(res => res.json())
      .then(body => {
        console.log(body);
        return body;
      })
      .catch(err => console.error(err));
  }

  get(url, data) {
    return this.request('GET', url, data);
  }

  put(url, data) {
    return this.request('PUT', url, data);
  }

  post(url, data) {
    return this.request('POST', url, data);
  }

  delete(url, data) {
    return this.request('DELETE', url, data);
  }
}

export default new ApiClient(config.apiRoot);
