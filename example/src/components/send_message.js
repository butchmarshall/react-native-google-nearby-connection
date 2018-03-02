import React from 'react';
import Prompt from 'react-native-prompt';

import {
	TouchableHighlight,
	StyleSheet,
	Switch,
	View,
	Text as Native_Text,
} from 'react-native';

import {
	Container,
	Content,
	Header,
	Title, Left, Body, Right,
	Card, CardItem,
	Button, Text, Icon,
} from 'native-base';

const defaultFunc = () => {};

class SendMessage extends React.Component {
	constructor(props) {
		super(props);

		this.state = {
			open: false
		};
	}

	handleSendMessage = (endpoint) => (value) => {
		if (this.props.onSendMessage) {
			this.props.onSendMessage(endpoint, value);
			this.handleCancel();
		}
	}

	handleCancel = () => {
		this.setState({
			open: false
		});
	}
	handleOpen = () => {
		this.setState({
			open: true
		});
	}
	
	render() {
		const {
			endpoint,
		} = this.props;

		if (endpoint.state !== "connected") {
			return null;
		}

		if (!endpoint || endpoint.state !== "connected") {
			return null;
		}

		return (
			<CardItem key={"send_file_"+endpoint.id}>
				<Prompt
					title="Send a message"
					placeholder="..."
					defaultValue=""
					visible={this.state.open}
					onCancel={this.handleCancel}
					onSubmit={this.handleSendMessage(endpoint)} />
				<Left>
					<Body>
						<Text>Send Message</Text>
					</Body>	
				</Left>
				<Right>
					<Button onPress={this.handleOpen}>
						<Icon name={"md-text"} />
					</Button>
				</Right>
			</CardItem>
		);
	}
};

export default SendMessage;