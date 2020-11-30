/**
 * @flow
 *
 * A container for maintaining threads-level state and actions
 */
import { connect } from 'react-redux';

import Threads from './threads';

import { getSortedThreads, getSelectedThreadId } from '../../selectors/threads';

import { selectThread } from '../../actions/threads';

const mapStateToProps = state => {
  return {
    threads: getSortedThreads(state),
    threadId: getSelectedThreadId(state),
    matches: state.search.matches
  };
};

export default connect(mapStateToProps, {
  selectThread
})(Threads);
