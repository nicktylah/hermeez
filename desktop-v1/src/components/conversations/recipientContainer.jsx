// @flow
import React, {Component, PropTypes} from 'react';

export default class RecipientContainer extends Component {
  static propTypes = {
    recipients: PropTypes.string
  };

  // shouldComponentUpdate(nextProps) {
  //   return nextProps.recipients !== '' && nextProps.recipients !== this.props.recipients;
  // }

  constructor(props) {
    super(props);
  }

  render() {
    return (
      <div className="recipient-container">
        <span className="id" title={this.props.recipients}>{this.props.recipients}</span>
      </div>
    );
  }

}
