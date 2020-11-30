// @flow
import React, {Component, PropTypes} from 'react';
import ColorPicker from "./colorPicker";

export default class RecipientOptions extends Component {
  static propTypes = {
    changeRecipientColor: PropTypes.func,
    recipient: PropTypes.object
  };

  constructor(props) {
    super(props);
    this.state = {
      visible: false,
      colorPickerVisible: false
    };

    document.addEventListener('click', () => {
      const colorPickerEls = document.getElementsByClassName('color-picker');
      if (colorPickerEls.length) {
        this.setState({ colorPickerVisible: false });
      }

      const els = document.getElementsByClassName('recipient-options');
      if (els.length) {
        this.setState({ visible: false });
      }
    });
  }

  static stopBubble(e) {
    e.nativeEvent.stopImmediatePropagation();
  }

  showOptions(e) {
    e.nativeEvent.stopImmediatePropagation();
    this.setState({ visible: true });
  }

  showColorPicker(e) {
    e.nativeEvent.stopImmediatePropagation();
    this.setState({ colorPickerVisible: true });
  }

  changeRecipientColorAndClose(phoneNumber: number, color: string) {
    return this.props.changeRecipientColor(phoneNumber, color)
      .then(() => {
        this.setState({
          visible: false,
          colorPickerVisible: false
        });
      });
  }

  render() {
    return (
      <span className="recipient-dropdown" onClick={this.showOptions.bind(this)}>
                ˇ
        {this.state.visible && <div className="recipient-options" onClick={RecipientOptions.stopBubble.bind(this)}>
          <div className="recipient-option-header">{this.props.recipient.phoneNumber}</div>
          <div className="recipient-option-color" onClick={this.showColorPicker.bind(this)}>
            Change Color
            {this.state.colorPickerVisible &&
            <ColorPicker
              changeRecipientColorAndClose={this.changeRecipientColorAndClose.bind(this)}
              phoneNumber={this.props.recipient.phoneNumber}
            />}
          </div>
        </div>}
              </span>
    )
  }
}
