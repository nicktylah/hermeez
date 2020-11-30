import { fork, take, takeEvery } from 'redux-saga/effects';

import { GET_THREADS, SEARCH_THREADS } from '../../actions/actionTypes';
import { fetchThreads, searchThreads } from './tasks';

export function* watchFetchThreads() {
  while (true) {
    const { payload } = yield take(GET_THREADS);
    yield fork(fetchThreads, payload);
  }
}

// export function* watchSearchThreads() {
//   yield takeEvery(SEARCH_THREADS, searchThreads);
// }
