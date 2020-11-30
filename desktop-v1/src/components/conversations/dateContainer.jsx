// @flow
import React, {Component, PropTypes} from 'react';

export default class DateContainer extends Component {
  static propTypes = {
    date: PropTypes.string
  };

  shouldComponentUpdate(nextProps) {
    return nextProps.date !== this.props.date;
  }

  constructor(props) {
    super(props);
  }

  render() {
    return (
      <div className="date-container">
        <span className="date">{this.props.date}</span>
      </div>
    );
  }

}
