// @flow
import React, {Component, PropTypes} from 'react';
import {colors} from '../../lib/colors';

export default class ColorPicker extends Component {
  static propTypes = {
    changeRecipientColorAndClose: PropTypes.func,
    phoneNumber: PropTypes.string
  };

  render() {
    const { phoneNumber, changeRecipientColorAndClose } = this.props;
    const colorEls = colors.map((color, i) => {
      return <div
        key={i}
        className="color" onClick={changeRecipientColorAndClose.bind(null, phoneNumber, color)}
        style={{ background: color }}
      />;
    });
    return (
      <div className="color-picker">
        {colorEls}
      </div>
    )
  }
}
