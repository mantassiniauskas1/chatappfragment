package com.jasonpyau.chatapp.service;

import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.jasonpyau.chatapp.entity.*;
import com.jasonpyau.chatapp.exception.*;
import com.jasonpyau.chatapp.form.*;
import com.jasonpyau.chatapp.repository.*;
import com.jasonpyau.chatapp.util.*;

@Service
public class GroupChatService {

    @Autowired
    private UserService userService;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private GroupChatRepository groupChatRepository;

    private final CustomValidator<GroupChat> groupChatValidator = new CustomValidator<>();
    private final CustomValidator<AddGroupChatUserForm> addUserFormValidator = new CustomValidator<>();
    private final CustomValidator<RenameGroupChatForm> renameGroupChatFormValidator = new CustomValidator<>();

    public GroupChat createNewGroupChat(User creator, NewGroupChatForm newGroupChatForm) {
        // Validate the input
        validateNewGroupChatInput(newGroupChatForm);

        // Create a new group chat
        GroupChat groupChat = createGroupChat(creator, newGroupChatForm);

        // Add users to the group chat
        addUserToGroupChat(creator, newGroupChatForm.getUsernames(), groupChat);

        return groupChat;
    }

    public Set<GroupChat> getUserGroupChats(User user) {
        return userService.findUserJoinedWithGroupChat(user.getUsername()).getGroupChats();
    }

    public Optional<GroupChat> findGroupChatById(Long id) {
        return groupChatRepository.findById(id);
    }

    public Message addUserToGroupChat(User user, AddGroupChatUserForm form, Long groupChatId) {
        // Validate the user and group chat
        GroupChat groupChat = validateUserInGroupChat(user, groupChatId);

        // Validate the input
        addUserFormValidator.validate(form);

        // Add a new user to the group chat
        User newUser = userService.findUserJoinedWithGroupChat(form.getUsername());
        if (groupChat.getUsers().contains(newUser)) {
            return null; // User is already in the group chat
        }

        // Create a message and update the group chat
        Message message = addUserToChat(user, newUser, groupChat);
        return message;
    }

    public Message removeUserFromGroupChat(User user, Long groupChatId) {
        // Validate the user and group chat
        GroupChat groupChat = validateUserInGroupChat(user, groupChatId);

        // Remove the user from the group chat and create a message
        Message message = removeUserFromChat(user, groupChat);
        return message;
    }

    public Message renameGroupChat(User user, RenameGroupChatForm form, Long groupChatId) {
        // Validate the user and group chat
        GroupChat groupChat = validateUserInGroupChat(user, groupChatId);

        // Validate the input
        renameGroupChatFormValidator.validate(form);

        // Rename the group chat and create a message
        Message message = renameChat(user, form, groupChat);
        return message;
    }

    // Helper methods

    private void validateNewGroupChatInput(NewGroupChatForm newGroupChatForm) {
        if (newGroupChatForm == null || newGroupChatForm.getUsernames() == null || newGroupChatForm.getUsernames().size() > 100) {
            throw new InvalidInputException("Invalid input");
        }
    }

    private GroupChat createGroupChat(User creator, NewGroupChatForm newGroupChatForm) {
        GroupChat groupChat = GroupChat.builder()
                .name(newGroupChatForm.getName())
                .lastMessageAt(DateFormat.getUnixTime())
                .build();
        groupChatValidator.validate(groupChat);
        groupChatRepository.save(groupChat);
        newGroupChatForm.getUsernames().add(creator.getUsername());
        return groupChatRepository.save(groupChat);
    }

    private void addUserToGroupChat(User creator, Set<String> usernames, GroupChat groupChat) {
        for (String username : usernames) {
            User member = userService.findUserJoinedWithGroupChat(username);
            groupChat.addToGroupChat(member);
        }
    }

    private GroupChat validateUserInGroupChat(User user, Long groupChatId) {
        Optional<GroupChat> optional = groupChatRepository.findById(groupChatId);
        if (!optional.isPresent()) {
            throw new InvalidGroupChatException();
        }
        GroupChat groupChat = optional.get();
        if (!groupChat.getUsers().contains(user)) {
            throw new InvalidUserException();
        }
        return groupChat;
    }

    private Message addUserToChat(User user, User newUser, GroupChat groupChat) {
        groupChat.addToGroupChat(newUser);
        Message message = Message.builder()
                .content(String.format("'@%s' was added to the chat by '@%s'.", newUser.getUsername(), user.getUsername()))
                .createdAt(DateFormat.getUnixTime())
                .modifiedAt(DateFormat.getUnixTime())
                .messageType(MessageType.USER_JOIN)
                .sender(user)
                .groupChat(groupChat)
                .build();
        groupChat.setLastMessageAt(DateFormat.getUnixTime());
        groupChatRepository.save(groupChat);
        return messageRepository.save(message);
    }

    private Message removeUserFromChat(User user, GroupChat groupChat) {
        user = userService.findUserJoinedWithGroupChat(user.getUsername());
        Message message = Message.builder()
                .content(String.format("'@%s' left the chat.", user.getUsername()))
                .createdAt(DateFormat.getUnixTime())
                .modifiedAt(DateFormat.getUnixTime())
                .messageType(MessageType.USER_LEAVE)
                .sender(user)
                .groupChat(groupChat)
                .build();
        groupChat.removeFromGroupChat(user);
        groupChat.setLastMessageAt(DateFormat.getUnixTime());
        groupChatRepository.save(groupChat);
        return messageRepository.save(message);
    }

    private Message renameChat(User user, RenameGroupChatForm form, GroupChat groupChat) {
        Message message = Message.builder()
                .content(String.format("'@%s' renamed the chat to '%s'", user.getUsername(), form.getName()))
                .createdAt(DateFormat.getUnixTime())
                .modifiedAt(DateFormat.getUnixTime())
                .messageType(MessageType.USER_RENAME)
                .sender(user)
                .groupChat(groupChat)
                .build();
        groupChat.setLastMessageAt(DateFormat.getUnixTime());
        groupChat.setName(form.getName());
        groupChatRepository.save(groupChat);
        return messageRepository.save(message);
    }
}
