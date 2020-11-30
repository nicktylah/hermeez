/**
 * @flow
 *
 * A container for maintaining messages-level state and actions
 */
import { connect } from 'react-redux';

import Messages from './messages';

const mapStateToProps = state => {
  return {
    contacts: state.threads.contactsByPhoneNumber,
    messages: state.messages
  };
};

export default connect(mapStateToProps, {})(Messages);
