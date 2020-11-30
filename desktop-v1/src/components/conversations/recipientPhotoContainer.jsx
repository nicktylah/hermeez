// @flow
import _ from 'lodash';
import React, {Component, PropTypes} from 'react';
import createAvatar from '../../lib/create-avatar';

export default class RecipientPhotoContainer extends Component {
  static propTypes = {
    recipients: PropTypes.object,
    lastMessageSender: PropTypes.string
  };

  shouldComponentUpdate(nextProps: Object) {
    return !_.isEqual(this.props, nextProps);
  }

  constructor(props: Object) {
    super(props);
  }

  render() {
    let photos;
    const { recipients, lastMessageSender } = this.props;
    const recipientsArray = _.values(recipients);
    const isLayered = Object.keys(recipients).length > 1;
    if (isLayered) {
      let lastMessageSender = recipients[lastMessageSender];
      const nonSelfRecipients = recipientsArray
        .filter((r) => {
          return r.phoneNumber !== lastMessageSender;
        });
      if (!lastMessageSender) {
        lastMessageSender = nonSelfRecipients[0];
      }
      const lastSenderAvatar = createAvatar(lastMessageSender, 1, true);
      const second = nonSelfRecipients[1];
      photos = [createAvatar(second, 0, true), lastSenderAvatar];
    } else {
      photos = recipientsArray
        .splice(0, 2) // Only first two recipients show photos
        .map((recipient, i) => {
          return createAvatar(recipient, i, isLayered);
        });
    }

    return (
      <div className="photos-container">
        {photos}
      </div>
    );
  }

}
