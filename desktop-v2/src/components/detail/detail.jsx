// @flow
import { connect } from 'react-redux';

import React, { Component, PropTypes } from 'react';
import { isEqual } from 'lodash';
import { PhoneNumberFormat, PhoneNumberUtil } from 'google-libphonenumber';

import { getSelectedThreadName } from '../../selectors/threads';

// import { updateContactColor } from '../../actions/contacts';
// import { updateThreadName } from '../../actions/threads';

import style from './detail.scss';

import type { Contact } from '../../types';

type Props = {
  contacts: Contact[],
  threadId: number,
  threadName: string
};

const phoneUtil = PhoneNumberUtil.getInstance();
const DEFAULT_COLOR = '#B8B9BA';

class Detail extends React.Component<Props> {
  shouldComponentUpdate(nextProps: Props) {
    return !isEqual(this.props, nextProps);
  }

  render() {
    let { contacts, threadId, threadName } = this.props;

    if (!threadId) {
      return <div />;
    }

    contacts = contacts || [];

    // TODO: Add thread-naming functionality
    const contactContainers = contacts.map(c => {
      let contactIdentifier = c.displayName;
      if (!contactIdentifier) {
        try {
          const number = phoneUtil.parseAndKeepRawInput(
            c.phoneNumber.toString(),
            'US'
          );
          contactIdentifier = phoneUtil.format(
            number,
            PhoneNumberFormat.INTERNATIONAL
          );
        } catch (err) {
          console.error(
            `Could not parse phone number from ${c.phoneNumber}`,
            err
          );
        }
      }
      return (
        <div
          className={style.contact}
          key={c.phoneNumber}
          style={{ background: c.color || DEFAULT_COLOR }}
        >
          {contactIdentifier}
        </div>
      );
    });

    return (
      <div style={{ display: 'inline-block' }}>
        {threadName && <span className={style.nameCont}>{threadName}</span>}
        {contactContainers}
      </div>
    );
  }
}

const mapStateToProps = state => {
  return {
    contacts: state.threads.contacts,
    threadId: state.messages.threadId,
    threadName: getSelectedThreadName(state)
  };
};

export default connect(mapStateToProps, {
  // updateContactColor,
  // updateThreadName
})(Detail);
