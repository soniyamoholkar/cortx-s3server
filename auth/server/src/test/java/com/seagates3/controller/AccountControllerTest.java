/*
 * Copyright (c) 2020 Seagate Technology LLC and/or its Affiliates
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * For any questions about this software or licensing,
 * please email opensource@seagate.com or cortx-questions@seagate.com.
 *
 */

package com.seagates3.controller;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.seagates3.authserver.AuthServerConfig;
import com.seagates3.dao.AccessKeyDAO;
import com.seagates3.dao.AccountDAO;
import com.seagates3.dao.DAODispatcher;
import com.seagates3.dao.DAOResource;
import com.seagates3.dao.RoleDAO;
import com.seagates3.dao.UserDAO;
import com.seagates3.dao.ldap.LDAPUtils;
import com.seagates3.exception.DataAccessException;
import com.seagates3.model.AccessKey;
import com.seagates3.model.Account;
import com.seagates3.model.Requestor;
import com.seagates3.model.Role;
import com.seagates3.model.User;
import com.seagates3.response.ServerResponse;
import com.seagates3.s3service.S3AccountNotifier;
import com.seagates3.service.AccessKeyService;
import com.seagates3.util.KeyGenUtil;

import io.netty.handler.codec.http.HttpResponseStatus;

@RunWith(PowerMockRunner.class)
    @PrepareForTest({DAODispatcher.class,    KeyGenUtil.class,
                     AuthServerConfig.class, AccountController.class,
                     AccessKeyService.class})
    @PowerMockIgnore(
        {"javax.management.*"}) public class AccountControllerTest {

    private final AccountController accountController;
    private
     final AccountController accountControllerWithKeys;
    private final AccountDAO accountDAO;
    private final UserDAO userDAO;
    private final AccessKeyDAO accessKeyDAO;
    private final RoleDAO roleDAO;
    private final Requestor requestor;
    private final S3AccountNotifier s3;
    private Map<String, String> requestBody = new HashMap<>();
    private
     Map<String, String> requestBodyWithKeys = new HashMap<>();

    public AccountControllerTest() throws Exception {
        PowerMockito.mockStatic(DAODispatcher.class);

        requestor = mock(Requestor.class);
        requestBody.put("AccountName", "s3test");
        requestBody.put("Email", "testuser@seagate.com");

        requestBodyWithKeys.put("AccountName", "s3test");
        requestBodyWithKeys.put("Email", "testuser@seagate.com");
        requestBodyWithKeys.put("AccessKey", "AKIASIAS");
        requestBodyWithKeys.put("SecretKey", "htuspscae/123");

        accountDAO = Mockito.mock(AccountDAO.class);
        userDAO = Mockito.mock(UserDAO.class);
        accessKeyDAO = Mockito.mock(AccessKeyDAO.class);
        roleDAO = Mockito.mock(RoleDAO.class);
        s3 = Mockito.mock(S3AccountNotifier.class);

        PowerMockito.whenNew(S3AccountNotifier.class)
            .withNoArguments()
            .thenReturn(s3);
        PowerMockito.doReturn(accountDAO)
            .when(DAODispatcher.class, "getResourceDAO", DAOResource.ACCOUNT);

        PowerMockito.doReturn(userDAO)
            .when(DAODispatcher.class, "getResourceDAO", DAOResource.USER);

        PowerMockito.doReturn(accessKeyDAO).when(
            DAODispatcher.class, "getResourceDAO", DAOResource.ACCESS_KEY);

        PowerMockito.doReturn(roleDAO)
            .when(DAODispatcher.class, "getResourceDAO", DAOResource.ROLE);

        accountController = new AccountController(requestor, requestBody);
        accountControllerWithKeys =
            new AccountController(requestor, requestBodyWithKeys);
    }

    @Before
    public void setUp() throws Exception {
        PowerMockito.mockStatic(DAODispatcher.class);
        PowerMockito.mockStatic(KeyGenUtil.class);
        PowerMockito.mockStatic(AuthServerConfig.class);
        PowerMockito.mockStatic(AccessKeyService.class);

        PowerMockito.doReturn("987654352188")
            .when(KeyGenUtil.class, "createAccountId");
        PowerMockito.doReturn("C1234").when(KeyGenUtil.class, "createId");
        PowerMockito.doReturn("can1234")
            .when(KeyGenUtil.class, "createCanonicalId");
        PowerMockito.doReturn("0000").when(AuthServerConfig.class, "getReqId");
        PowerMockito.doReturn(new ArrayList<String>())
            .when(AuthServerConfig.class, "getS3InternalAccounts");
        PowerMockito.doReturn(1000)
            .when(AuthServerConfig.class, "getMaxAccountLimit");
    }

    @Test
    public void ListAccounts_AccountsSearchFailed_ReturnInternalServerError()
            throws Exception {
        Mockito.when(accountDAO.findAll()).thenThrow(
                new DataAccessException("Failed to fetch accounts.\n"));

        final String expectedResponseBody =
            "<?xml version=\"1.0\" " +
            "encoding=\"UTF-8\" standalone=\"no\"?>" + "<ErrorResponse " +
            "xmlns=\"https://iam.seagate.com/doc/2010-05-08/\">" +
            "<Error><Code>InternalFailure</Code>" +
            "<Message>The request processing has failed because of an " +
            "unknown error, exception or failure.</Message></Error>" +
            "<RequestId>0000</RequestId>" + "</ErrorResponse>";

        ServerResponse response = accountController.list();
        Assert.assertEquals(expectedResponseBody, response.getResponseBody());
        Assert.assertEquals(HttpResponseStatus.INTERNAL_SERVER_ERROR,
                response.getResponseStatus());
    }

    @Test
    public void ListAccounts_AccountsListEmpty_ReturnListAccountsResponse()
            throws Exception {
        Account[] expectedAccountList = new Account[0];

        Mockito.doReturn(expectedAccountList).when(accountDAO).findAll();

        final String expectedResponseBody =
            "<?xml version=\"1.0\" " +
            "encoding=\"UTF-8\" standalone=\"no\"?>" +
            "<ListAccountsResponse " +
            "xmlns=\"https://iam.seagate.com/doc/2010-05-08/\">" +
            "<ListAccountsResult>" + "<Accounts/>" +
            "<IsTruncated>false</IsTruncated>" + "</ListAccountsResult>" +
            "<ResponseMetadata>" + "<RequestId>0000</RequestId>" +
            "</ResponseMetadata>" + "</ListAccountsResponse>";

        ServerResponse response = accountController.list();
        Assert.assertEquals(expectedResponseBody, response.getResponseBody());
        Assert.assertEquals(HttpResponseStatus.OK,
                            response.getResponseStatus());
    }

    @Test
    public void ListAccounts_AccountsSearchSuccess_ReturnListAccountsResponse()
            throws Exception {
        Account expectedAccount = new Account();
        expectedAccount.setName("s3test");
        expectedAccount.setId("123456789012");
        expectedAccount.setCanonicalId("canonicalid");
        expectedAccount.setEmail("user.name@seagate.com");
        Account[] expectedAccountList = new Account[]{expectedAccount};

        Mockito.doReturn(expectedAccountList).when(accountDAO).findAll();

        final String expectedResponseBody =
            "<?xml version=\"1.0\" " +
            "encoding=\"UTF-8\" standalone=\"no\"?>" +
            "<ListAccountsResponse " +
            "xmlns=\"https://iam.seagate.com/doc/2010-05-08/\">" +
            "<ListAccountsResult>" + "<Accounts>" + "<member>" +
            "<AccountName>s3test</AccountName>" +
            "<AccountId>123456789012</AccountId>" +
            "<CanonicalId>canonicalid</CanonicalId>" +
            "<Email>user.name@seagate.com</Email>" + "</member>" +
            "</Accounts>" + "<IsTruncated>false</IsTruncated>" +
            "</ListAccountsResult>" + "<ResponseMetadata>" +
            "<RequestId>0000</RequestId>" + "</ResponseMetadata>" +
            "</ListAccountsResponse>";

        ServerResponse response = accountController.list();
        Assert.assertEquals(expectedResponseBody, response.getResponseBody());
        Assert.assertEquals(HttpResponseStatus.OK,
                            response.getResponseStatus());
    }

    @Test
    public void CreateAccount_AccountSearchFailed_ReturnInternalServerError()
            throws Exception {
        Mockito.when(accountDAO.find("s3test")).thenThrow(
                new DataAccessException("failed to search account.\n"));
        Mockito.doReturn(new Account[0]).when(accountDAO).findAll();
        final String expectedResponseBody =
            "<?xml version=\"1.0\" " +
            "encoding=\"UTF-8\" standalone=\"no\"?>" + "<ErrorResponse " +
            "xmlns=\"https://iam.seagate.com/doc/2010-05-08/\">" +
            "<Error><Code>InternalFailure</Code>" +
            "<Message>The request processing has failed because of an " +
            "unknown error, exception or failure.</Message></Error>" +
            "<RequestId>0000</RequestId>" + "</ErrorResponse>";

        ServerResponse response = accountController.create();
        Assert.assertEquals(expectedResponseBody, response.getResponseBody());
        Assert.assertEquals(HttpResponseStatus.INTERNAL_SERVER_ERROR,
                response.getResponseStatus());
    }

    @Test
    public void CreateAccount_AccountExists_ReturnEntityAlreadyExists()
            throws Exception {
        Account account = new Account();
        account.setId("123456789012");
        account.setName("s3test");

        Mockito.when(accountDAO.find("s3test")).thenReturn(account);
        Mockito.doReturn(new Account[1]).when(accountDAO).findAll();
        final String expectedResponseBody =
            "<?xml version=\"1.0\" " +
            "encoding=\"UTF-8\" standalone=\"no\"?>" + "<ErrorResponse " +
            "xmlns=\"https://iam.seagate.com/doc/2010-05-08/\">" +
            "<Error><Code>EntityAlreadyExists</Code>" +
            "<Message>The request was rejected because it attempted to " +
            "create an account that already exists.</Message></Error>" +
            "<RequestId>0000</RequestId>" + "</ErrorResponse>";

        ServerResponse response = accountController.create();
        Assert.assertEquals(expectedResponseBody, response.getResponseBody());
        Assert.assertEquals(HttpResponseStatus.CONFLICT,
                response.getResponseStatus());
    }

    @Test public void
    CreateAccount_UniqueCanonicalIdGenerationFailed_ReturnInternalServerError()
        throws Exception {
      Account account = new Account();
      account.setName("s3test");

      Mockito.when(accountDAO.find("s3test")).thenReturn(account);
      Mockito.doReturn(account).when(accountDAO).findByCanonicalID("can1234");
      Mockito.doReturn(new Account[0]).when(accountDAO).findAll();

      final String expectedResponseBody =
          "<?xml version=\"1.0\" " + "encoding=\"UTF-8\" standalone=\"no\"?>" +
          "<ErrorResponse xmlns=\"https://iam.seagate.com/doc/2010-05-08/\">" +
          "<Error><Code>InternalFailure</Code>" +
          "<Message>The request processing has failed because of an " +
          "unknown error, exception or failure.</Message></Error>" +
          "<RequestId>0000</RequestId>" + "</ErrorResponse>";

      ServerResponse response = accountController.create();
      Assert.assertEquals(expectedResponseBody, response.getResponseBody());
      Assert.assertEquals(HttpResponseStatus.INTERNAL_SERVER_ERROR,
                          response.getResponseStatus());
    }

    @Test
    public void CreateAccount_AccountSaveFailed_ReturnInternalServerError()
            throws Exception {
        Account account = new Account();
        account.setName("s3test");

        Mockito.doReturn(account).when(accountDAO).find("s3test");
        Mockito.doReturn(new Account[0]).when(accountDAO).findAll();
        Mockito.doThrow(new DataAccessException("failed to add new account.\n"))
            .when(accountDAO)
            .save(account);
        Mockito.doReturn(new Account()).when(accountDAO).findByCanonicalID(
            "can1234");

        final String expectedResponseBody =
            "<?xml version=\"1.0\" " +
            "encoding=\"UTF-8\" standalone=\"no\"?>" + "<ErrorResponse " +
            "xmlns=\"https://iam.seagate.com/doc/2010-05-08/\">" +
            "<Error><Code>InternalFailure</Code>" +
            "<Message>The request processing has failed because of an " +
            "unknown error, exception or failure.</Message></Error>" +
            "<RequestId>0000</RequestId>" + "</ErrorResponse>";

        ServerResponse response = accountController.create();
        Assert.assertEquals(expectedResponseBody, response.getResponseBody());
        Assert.assertEquals(HttpResponseStatus.INTERNAL_SERVER_ERROR,
                response.getResponseStatus());
    }

    @Test
    public void CreateAccount_FailedToCreateRootUser_ReturnInternalServerError()
            throws Exception {
        Account account = new Account();
        account.setName("s3test");

        Mockito.doReturn(account).when(accountDAO).find("s3test");
        Mockito.doReturn(new Account[0]).when(accountDAO).findAll();
        Mockito.doNothing().when(accountDAO).save(any(Account.class));
        Mockito.doThrow(new DataAccessException("failed to save new user.\n"))
                .when(userDAO).save(any(User.class));
        Mockito.doReturn(new Account()).when(accountDAO).findByCanonicalID(
            "can1234");

        final String expectedResponseBody =
            "<?xml version=\"1.0\" " +
            "encoding=\"UTF-8\" standalone=\"no\"?>" + "<ErrorResponse " +
            "xmlns=\"https://iam.seagate.com/doc/2010-05-08/\">" +
            "<Error><Code>InternalFailure</Code>" +
            "<Message>The request processing has failed because of an " +
            "unknown error, exception or failure.</Message></Error>" +
            "<RequestId>0000</RequestId>" + "</ErrorResponse>";

        ServerResponse response = accountController.create();
        Assert.assertEquals(expectedResponseBody, response.getResponseBody());
        Assert.assertEquals(HttpResponseStatus.INTERNAL_SERVER_ERROR,
                response.getResponseStatus());
    }

    @Test
    public void CreateAccount_FailedToCreateRootAccessKey_ReturnInternalServerError()
            throws Exception {
        Account account = new Account();
        account.setName("s3test");

        Mockito.doReturn(new Account[0]).when(accountDAO).findAll();
        Mockito.doReturn(account).when(accountDAO).find("s3test");
        Mockito.doNothing().when(accountDAO).save(any(Account.class));
        Mockito.doNothing().when(userDAO).save(any(User.class));
        PowerMockito.doThrow(new DataAccessException(
                                 "failed to save root access key.\n"))
            .when(AccessKeyService.class, "createAccessKey", any(User.class));
        Mockito.doReturn(new Account()).when(accountDAO).findByCanonicalID(
            "can1234");

        final String expectedResponseBody =
            "<?xml version=\"1.0\" " +
            "encoding=\"UTF-8\" standalone=\"no\"?>" + "<ErrorResponse " +
            "xmlns=\"https://iam.seagate.com/doc/2010-05-08/\">" +
            "<Error><Code>InternalFailure</Code>" +
            "<Message>The request processing has failed because of an " +
            "unknown error, exception or failure.</Message></Error>" +
            "<RequestId>0000</RequestId>" + "</ErrorResponse>";

        ServerResponse response = accountController.create();
        Assert.assertEquals(expectedResponseBody, response.getResponseBody());
        Assert.assertEquals(HttpResponseStatus.INTERNAL_SERVER_ERROR,
                response.getResponseStatus());
    }

    @Test public void CreateAccount_Success_ReturnCreateResponse()
        throws Exception {
        Account account = new Account();
        account.setName("s3test");
        ServerResponse resp = new ServerResponse();
        resp.setResponseStatus(HttpResponseStatus.OK);

        mockCreateAccessKey();

        Mockito.doReturn(new Account[0]).when(accountDAO).findAll();
        Mockito.doReturn(account).when(accountDAO).find("s3test");
        Mockito.doNothing().when(accountDAO).save(any(Account.class));
        Mockito.doNothing().when(userDAO).save(any(User.class));
        Mockito.doReturn(resp).when(s3).notifyNewAccount(
            any(String.class), any(String.class), any(String.class));
        Mockito.doReturn(new Account()).when(accountDAO).findByCanonicalID(
            "can1234");

        final String expectedResponseBody =
            "<?xml version=\"1.0\" " +
            "encoding=\"UTF-8\" standalone=\"no\"?>" +
            "<CreateAccountResponse " +
            "xmlns=\"https://iam.seagate.com/doc/2010-05-08/\">" +
            "<CreateAccountResult>" + "<Account>" +
            "<AccountId>987654352188</AccountId>" +
            "<CanonicalId>can1234</CanonicalId>" +
            "<AccountName>s3test</AccountName>" +
            "<RootUserName>root</RootUserName>" +
            "<AccessKeyId>AKIASIAS</AccessKeyId>" +
            "<RootSecretKeyId>htuspscae/123</RootSecretKeyId>" +
            "<Status>Active</Status>" + "</Account>" +
            "</CreateAccountResult>" + "<ResponseMetadata>" +
            "<RequestId>0000</RequestId>" + "</ResponseMetadata>" +
            "</CreateAccountResponse>";

        ServerResponse response = accountController.create();
        Assert.assertEquals(expectedResponseBody, response.getResponseBody());
        Assert.assertEquals(HttpResponseStatus.CREATED,
                response.getResponseStatus());
    }

    @Test public void createAccountWithKeysReturnSuccessCreateResponse()
        throws Exception {
      Account account = new Account();
      account.setName("s3test");
      ServerResponse resp = new ServerResponse();
      resp.setResponseStatus(HttpResponseStatus.OK);

      mockCreateAccessKeyWithKeys();

      Mockito.doReturn(new Account[0]).when(accountDAO).findAll();
      Mockito.doReturn(account).when(accountDAO).find("s3test");
      Mockito.doNothing().when(accountDAO).save(any(Account.class));
      Mockito.doNothing().when(userDAO).save(any(User.class));
      Mockito.doReturn(new AccessKey()).when(accessKeyDAO).find(
          any(String.class));
      Mockito.doReturn(resp).when(s3).notifyNewAccount(
          any(String.class), any(String.class), any(String.class));
      Mockito.doReturn(new Account()).when(accountDAO).findByCanonicalID(
          "can1234");

      final String expectedResponseBody =
          "<?xml version=\"1.0\" " + "encoding=\"UTF-8\" standalone=\"no\"?>" +
          "<CreateAccountResponse " +
          "xmlns=\"https://iam.seagate.com/doc/2010-05-08/\">" +
          "<CreateAccountResult>" + "<Account>" +
          "<AccountId>987654352188</AccountId>" +
          "<CanonicalId>can1234</CanonicalId>" +
          "<AccountName>s3test</AccountName>" +
          "<RootUserName>root</RootUserName>" +
          "<AccessKeyId>AKIASIASCustom</AccessKeyId>" +
          "<RootSecretKeyId>htuspscae/123/custom</RootSecretKeyId>" +
          "<Status>Active</Status>" + "</Account>" + "</CreateAccountResult>" +
          "<ResponseMetadata>" + "<RequestId>0000</RequestId>" +
          "</ResponseMetadata>" + "</CreateAccountResponse>";

      ServerResponse response = accountControllerWithKeys.create();
      Assert.assertEquals(expectedResponseBody, response.getResponseBody());
      Assert.assertEquals(HttpResponseStatus.CREATED,
                          response.getResponseStatus());
    }

    @Test public void
    CreateAccount_AccessKeyExists_ReturnAccessKeyAlreadyExists()
        throws Exception {
      Account account = new Account();
      account.setName("s3test");

      AccessKey accessKey = new AccessKey();
      accessKey.setUserId("UserId");

      Mockito.doReturn(new Account[0]).when(accountDAO).findAll();
      Mockito.doReturn(account).when(accountDAO).find("s3test");
      Mockito.doReturn(accessKey).when(accessKeyDAO).find(any(String.class));
      Mockito.doReturn(new Account()).when(accountDAO).findByCanonicalID(
          "can1234");

      final String expectedResponseBody =
          "<?xml version=\"1.0\" " + "encoding=\"UTF-8\" standalone=\"no\"?>" +
          "<ErrorResponse " +
          "xmlns=\"https://iam.seagate.com/doc/2010-05-08/\">" +
          "<Error><Code>AccessKeyAlreadyExists</Code>" +
          "<Message>The request was rejected because " +
          "account with this access key already exists.</Message></Error>" +
          "<RequestId>0000</RequestId>" + "</ErrorResponse>";

      ServerResponse response = accountControllerWithKeys.create();
      Assert.assertEquals(expectedResponseBody, response.getResponseBody());
      Assert.assertEquals(HttpResponseStatus.CONFLICT,
                          response.getResponseStatus());
    }

    @Test public void CreateAccount_ReturnMaxAccountLimitExceeded()
        throws Exception {
      PowerMockito.doReturn(0)
          .when(AuthServerConfig.class, "getMaxAccountLimit");

      Mockito.doReturn(new Account[1]).when(accountDAO).findAll();
      final String expectedResponseBody =
          "<?xml version=\"1.0\" " + "encoding=\"UTF-8\" standalone=\"no\"?>" +
          "<ErrorResponse " +
          "xmlns=\"https://iam.seagate.com/doc/2010-05-08/\">" +
          "<Error><Code>MaxAccountLimitExceeded</Code>" +
          "<Message>The request was rejected because " +
          "maximum limit(i.e 0) of account creation has " +
          "exceeded.</Message></Error>" + "<RequestId>0000</RequestId>" +
          "</ErrorResponse>";

      ServerResponse response = accountController.create();
      Assert.assertEquals(expectedResponseBody, response.getResponseBody());
      Assert.assertEquals(HttpResponseStatus.FORBIDDEN,
                          response.getResponseStatus());
    }

    @Test public void
    CreateAccount_ConcurrencyIssues_ReturnInternalServerError()
        throws Exception {
      PowerMockito.when(AuthServerConfig.class, "getMaxAccountLimit")
          .thenReturn(1);
      List<String> internalacc = new ArrayList<>();
      internalacc.add("abcd");
      PowerMockito.when(AuthServerConfig.class, "getS3InternalAccounts")
          .thenReturn(internalacc);
      PowerMockito.when(accountDAO, "findAll")
          .thenReturn(new Account[0])
          .thenReturn(new Account[3]);
      Account account = new Account();
      account.setName("s3test");

      mockCreateAccessKey();

      ServerResponse resp = new ServerResponse();
      resp.setResponseStatus(HttpResponseStatus.OK);

      Mockito.doReturn(account).when(accountDAO).find("s3test");
      Mockito.doNothing().when(accountDAO).save(any(Account.class));
      Mockito.doNothing().when(userDAO).save(any(User.class));
      Mockito.doNothing().when(accessKeyDAO).save(any(AccessKey.class));
      Mockito.doReturn(resp).when(s3).notifyNewAccount(
          any(String.class), any(String.class), any(String.class));
      Mockito.doReturn(new Account()).when(accountDAO).findByCanonicalID(
          "can1234");

      final String expectedResponseBody =
          "<?xml version=\"1.0\" " + "encoding=\"UTF-8\" standalone=\"no\"?>" +
          "<ErrorResponse xmlns=\"https://iam.seagate.com/doc/2010-05-08/\">" +
          "<Error><Code>InternalFailure</Code>" +
          "<Message>The request processing has failed because of an " +
          "unknown error, exception or failure.</Message></Error>" +
          "<RequestId>0000</RequestId>" + "</ErrorResponse>";

      ServerResponse response = accountController.create();
      Assert.assertEquals(expectedResponseBody, response.getResponseBody());
      Assert.assertEquals(HttpResponseStatus.INTERNAL_SERVER_ERROR,
                          response.getResponseStatus());
    }

    @Test public void
    ResetAccountAccessKey_AccountDoesNotExists_ReturnNoSuchEntity()
        throws Exception {
        Account account = mock(Account.class);
        Mockito.when(accountDAO.find("s3test")).thenReturn(account);
        Mockito.when(account.exists()).thenReturn(Boolean.FALSE);

        final String expectedResponseBody =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"" +
            " standalone=\"no\"?><ErrorResponse " +
            "xmlns=\"https://iam.seagate.com/" +
            "doc/2010-05-08/\"><Error><Code>NoSuchEntity</Code><Message>The " +
            "request" +
            " was rejected because it referenced an entity that does not " +
            "exist. " +
            "</Message></Error><RequestId>0000</RequestId></ErrorResponse>";

        ServerResponse response = accountController.resetAccountAccessKey();
        Assert.assertEquals(expectedResponseBody, response.getResponseBody());
        Assert.assertEquals(HttpResponseStatus.NOT_FOUND,
                            response.getResponseStatus());
    }

    @Test
    public void ResetAccountAccessKey_UserDAOException_InternalError()
            throws Exception {
        Account account = mock(Account.class);
        Mockito.when(accountDAO.find("s3test")).thenReturn(account);
        Mockito.when(account.exists()).thenReturn(Boolean.TRUE);
        Mockito.when(account.getName()).thenReturn("root");
        Mockito.doThrow(new DataAccessException("failed to get root user"))
            .when(userDAO)
            .find(any(String.class), any(String.class));

        final String expectedResponseBody =
            "<?xml version=\"1.0\" " +
            "encoding=\"UTF-8\" standalone=\"no\"?>" + "<ErrorResponse " +
            "xmlns=\"https://iam.seagate.com/doc/2010-05-08/\">" +
            "<Error><Code>InternalFailure</Code>" +
            "<Message>The request processing has failed because of an " +
            "unknown error, exception or failure.</Message></Error>" +
            "<RequestId>0000</RequestId>" + "</ErrorResponse>";

        ServerResponse response = accountController.resetAccountAccessKey();
        Assert.assertEquals(expectedResponseBody, response.getResponseBody());
        Assert.assertEquals(HttpResponseStatus.INTERNAL_SERVER_ERROR,
                response.getResponseStatus());
    }

    @Test
    public void ResetAccountAccessKey_NoRootUser_ReturnNoSuchEntity()
            throws Exception {
        Account account = mock(Account.class);
        User root = mock(User.class);
        Mockito.when(accountDAO.find("s3test")).thenReturn(account);
        Mockito.when(account.exists()).thenReturn(Boolean.TRUE);
        Mockito.when(account.getName()).thenReturn("root");
        Mockito.when(userDAO.find(any(String.class), any(String.class)))
            .thenReturn(root);
        Mockito.when(root.exists()).thenReturn(Boolean.FALSE);

        final String expectedResponseBody =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"" +
            " standalone=\"no\"?><ErrorResponse " +
            "xmlns=\"https://iam.seagate.com/" +
            "doc/2010-05-08/\"><Error><Code>NoSuchEntity</Code><Message>The " +
            "request" +
            " was rejected because it referenced an entity that does not " +
            "exist. " +
            "</Message></Error><RequestId>0000</RequestId></ErrorResponse>";

        ServerResponse response = accountController.resetAccountAccessKey();
        Assert.assertEquals(expectedResponseBody, response.getResponseBody());
        Assert.assertEquals(HttpResponseStatus.NOT_FOUND,
                            response.getResponseStatus());
    }

    @Test public void ResetAccountAccessKey_Success_Return() throws Exception {
        Account account = new Account();
        account.setName("s3test");
        account.setId("987654352188");
        account.setCanonicalId("can1234");
        User root = new User();
        root.setName("root");
        root.setId("AKIASIAS");
        AccessKey[] accessKeys = new AccessKey[1];

        mockCreateAccessKey();

        accessKeys[0] = mock(AccessKey.class);
        Mockito.when(accountDAO.find("s3test")).thenReturn(account);
        Mockito.when(userDAO.find(any(String.class), any(String.class)))
            .thenReturn(root);

        Mockito.when(accessKeyDAO.findAll(root)).thenReturn(accessKeys);
        Mockito.doNothing().when(accessKeyDAO).delete(accessKeys[0]);

        Mockito.doNothing().when(accessKeyDAO).save(any(AccessKey.class));

        final String expectedResponseBody =
            "<?xml version=\"1.0\" " +
            "encoding=\"UTF-8\" standalone=\"no\"?>" +
            "<ResetAccountAccessKeyResponse " +
            "xmlns=\"https://iam.seagate.com/doc/2010-05-08/\">" +
            "<ResetAccountAccessKeyResult>" + "<Account>" +
            "<AccountId>987654352188</AccountId>" +
            "<CanonicalId>can1234</CanonicalId>" +
            "<AccountName>s3test</AccountName>" +
            "<RootUserName>root</RootUserName>" +
            "<AccessKeyId>AKIASIAS</AccessKeyId>" +
            "<RootSecretKeyId>htuspscae/123</RootSecretKeyId>" +
            "<Status>Active</Status>" + "</Account>" +
            "</ResetAccountAccessKeyResult>" + "<ResponseMetadata>" +
            "<RequestId>0000</RequestId>" + "</ResponseMetadata>" +
            "</ResetAccountAccessKeyResponse>";

        ServerResponse response = accountController.resetAccountAccessKey();
        Assert.assertEquals(expectedResponseBody, response.getResponseBody());
        Assert.assertEquals(HttpResponseStatus.CREATED,
                response.getResponseStatus());
    }

    @Test
    public void DeleteAccount_AccountsSearchFailed_ReturnInternalServerError()
            throws DataAccessException {
        Mockito.when(accountDAO.find("s3test")).thenThrow(
                new DataAccessException("Failed to fetch accounts.\n"));

        final String expectedResponseBody =
            "<?xml version=\"1.0\" " +
            "encoding=\"UTF-8\" standalone=\"no\"?>" + "<ErrorResponse " +
            "xmlns=\"https://iam.seagate.com/doc/2010-05-08/\">" +
            "<Error><Code>InternalFailure</Code>" +
            "<Message>The request processing has failed because of an " +
            "unknown error, exception or failure.</Message></Error>" +
            "<RequestId>0000</RequestId>" + "</ErrorResponse>";

        ServerResponse response = accountController.delete();
        Assert.assertEquals(expectedResponseBody, response.getResponseBody());
        Assert.assertEquals(HttpResponseStatus.INTERNAL_SERVER_ERROR,
                response.getResponseStatus());
    }

    @Test
    public void DeleteAccount_AccountDoesNotExists_ReturnNoSuchEntity()
            throws Exception {
        Account account = mock(Account.class);
        Mockito.when(accountDAO.find("s3test")).thenReturn(account);
        Mockito.when(account.exists()).thenReturn(Boolean.FALSE);

        final String expectedResponseBody =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"" +
            " standalone=\"no\"?><ErrorResponse " +
            "xmlns=\"https://iam.seagate.com/" +
            "doc/2010-05-08/\"><Error><Code>NoSuchEntity</Code><Message>The " +
            "request" +
            " was rejected because it referenced an entity that does not " +
            "exist. " +
            "</Message></Error><RequestId>0000</RequestId></ErrorResponse>";

        ServerResponse response = accountController.delete();
        Assert.assertEquals(expectedResponseBody, response.getResponseBody());
        Assert.assertEquals(HttpResponseStatus.NOT_FOUND,
                            response.getResponseStatus());
    }

    @Test
    public void DeleteAccount_AccountExists_ReturnUnauthorisedResponse()
            throws Exception {
        User[] users = new User[1];
        users[0] = new User();
        users[0].setName("root");
        users[0].setId("rootxyz");
        AccessKey[] accessKeys = new AccessKey[1];
        accessKeys[0] = mock(AccessKey.class);
        Account account = mock(Account.class);

        Mockito.when(account.getName()).thenReturn("s3test");
        Mockito.when(accountDAO.find("s3test")).thenReturn(account);
        Mockito.when(account.exists()).thenReturn(Boolean.TRUE);
        Mockito.when(userDAO.find("s3test", "root")).thenReturn(users[0]);
        Mockito.when(requestor.getId()).thenReturn("abcxyz");
        Mockito.when(userDAO.findAll("s3test", "/")).thenReturn(users);
        Mockito.when(accessKeyDAO.findAll(users[0])).thenReturn(accessKeys);

        final String expectedResponseBody =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" " +
            "standalone=\"no\"?><ErrorResponse " +
            "xmlns=\"https://iam.seagate.com/doc" +
            "/2010-05-08/\"><Error><Code>UnauthorizedOperation</" +
            "Code><Message>You " +
            "are not authorized to perform this operation. Check your IAM " +
            "policies" +
            ", and ensure that you are using the correct access keys. " +
            "</Message>" +
            "</Error><RequestId>0000</RequestId></ErrorResponse>";

        ServerResponse response = accountController.delete();
        Assert.assertEquals(expectedResponseBody, response.getResponseBody());
        Assert.assertEquals(HttpResponseStatus.UNAUTHORIZED,
                            response.getResponseStatus());
    }

    @Test
    public void DeleteAccount_AccountExists_ReturnDeleteResponse()
            throws Exception {
        User[] users = new User[1];
        users[0] = new User();
        users[0].setName("root");
        users[0].setId("rootxyz");
        AccessKey aKey = new AccessKey();

        aKey.setId("akey123");
        aKey.setSecretKey("skey1234");

        ServerResponse resp = new ServerResponse();
        resp.setResponseStatus(HttpResponseStatus.OK);

        AccessKey[] accessKeys = new AccessKey[1];
        accessKeys[0] = mock(AccessKey.class);
        Account account = mock(Account.class);

        Mockito.when(account.getName()).thenReturn("s3test");
        Mockito.when(accountDAO.find("s3test")).thenReturn(account);
        Mockito.when(account.exists()).thenReturn(Boolean.TRUE);
        Mockito.when(userDAO.find("s3test", "root")).thenReturn(users[0]);
        Mockito.when(requestor.getId()).thenReturn("rootxyz");
        Mockito.when(requestor.getAccesskey()).thenReturn(aKey);
        Mockito.when(userDAO.findAll("s3test", "/")).thenReturn(users);
        Mockito.when(accessKeyDAO.findAll(users[0])).thenReturn(accessKeys);
        Mockito.doReturn(resp).when(s3).notifyDeleteAccount(
            any(String.class), any(String.class), any(String.class),
            any(String.class));

        final String expectedResponseBody =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" " +
            "standalone=\"no\"?><DeleteAccountResponse " +
            "xmlns=\"https://iam.seagate" +
            ".com/doc/2010-05-08/\"><ResponseMetadata><RequestId>0000</" +
            "RequestId><" + "/ResponseMetadata></DeleteAccountResponse>";

        ServerResponse response = accountController.delete();
        Assert.assertEquals(expectedResponseBody, response.getResponseBody());
        Assert.assertEquals(HttpResponseStatus.OK,
                            response.getResponseStatus());

        Mockito.verify(accessKeyDAO).delete(accessKeys[0]);
        Mockito.verify(userDAO).delete(users[0]);
        Mockito.verify(accountDAO).deleteOu(account, LDAPUtils.USER_OU);
        Mockito.verify(accountDAO).deleteOu(account, LDAPUtils.ROLE_OU);
        Mockito.verify(accountDAO).deleteOu(account, LDAPUtils.GROUP_OU);
        Mockito.verify(accountDAO).deleteOu(account, LDAPUtils.POLICY_OU);
        Mockito.verify(accountDAO).delete(account);
    }

    @Test
    public void DeleteAccount_HasSubResources_ReturnDeleteConflict()
            throws Exception {
        User[] users = new User[2];
        users[0] = new User();
        users[0].setName("root");
        users[0].setId("rootxyz");
        users[1] = new User();
        users[1].setName("s3user");

        AccessKey aKey = new AccessKey();

        aKey.setId("akey123");
        aKey.setSecretKey("skey1234");

        ServerResponse resp = new ServerResponse();
        resp.setResponseStatus(HttpResponseStatus.OK);

        AccessKey[] accessKeys = new AccessKey[1];
        accessKeys[0] = mock(AccessKey.class);
        Account account = mock(Account.class);

        Mockito.when(account.getName()).thenReturn("s3test");
        Mockito.when(accountDAO.find("s3test")).thenReturn(account);
        Mockito.when(account.exists()).thenReturn(Boolean.TRUE);
        Mockito.when(userDAO.findAll("s3test", "/")).thenReturn(users);
        Mockito.when(userDAO.find("s3test", "root")).thenReturn(users[0]);
        Mockito.when(requestor.getId()).thenReturn("rootxyz");
        Mockito.when(requestor.getAccesskey()).thenReturn(aKey);
        Mockito.when(accessKeyDAO.findAll(users[0])).thenReturn(accessKeys);
        Mockito.doThrow(new DataAccessException(
                            "subordinate objects must be deleted first"))
            .when(accountDAO)
            .deleteOu(account, LDAPUtils.USER_OU);
        Mockito.doReturn(resp).when(s3).notifyDeleteAccount(
            any(String.class), any(String.class), any(String.class),
            any(String.class));

        final String expectedResponseBody =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" " +
            "standalone=\"no\"?><ErrorResponse " +
            "xmlns=\"https://iam.seagate.com/doc" +
            "/2010-05-08/\"><Error><Code>DeleteConflict</Code><Message>The " +
            "request" +
            " was rejected because it attempted to delete a resource that " +
            "has " + "attached " +
            "subordinate entities. The error message describes these " +
            "entities.</Message>" +
            "</Error><RequestId>0000</RequestId></ErrorResponse>";

        ServerResponse response = accountController.delete();
        Assert.assertEquals(expectedResponseBody, response.getResponseBody());
        Assert.assertEquals(HttpResponseStatus.CONFLICT,
                            response.getResponseStatus());

        Mockito.verify(accessKeyDAO, times(0)).delete(accessKeys[0]);
        Mockito.verify(userDAO, times(0)).delete(users[0]);
        Mockito.verify(accountDAO).deleteOu(account, LDAPUtils.USER_OU);
    }

    @Test
    public void DeleteAccount_ForceDelete_ReturnDeleteResponse()
            throws Exception {
        requestBody.put("force", "true");
        User[] users = new User[1];
        users[0] = new User();
        users[0].setName("root");
        users[0].setId("rootxyz");

        AccessKey aKey = new AccessKey();

        aKey.setId("akey123");
        aKey.setSecretKey("skey1234");

        AccessKey[] accessKeys = new AccessKey[1];
        accessKeys[0] = mock(AccessKey.class);
        Role[] roles = new Role[1];
        roles[0] = mock(Role.class);
        Account account = mock(Account.class);

        ServerResponse resp = new ServerResponse();
        resp.setResponseStatus(HttpResponseStatus.OK);

        Mockito.when(account.getName()).thenReturn("s3test");
        Mockito.when(accountDAO.find("s3test")).thenReturn(account);
        Mockito.when(account.exists()).thenReturn(Boolean.TRUE);
        Mockito.when(userDAO.find("s3test", "root")).thenReturn(users[0]);
        Mockito.when(requestor.getId()).thenReturn("rootxyz");
        Mockito.when(requestor.getAccesskey()).thenReturn(aKey);
        Mockito.when(userDAO.findAll("s3test", "/")).thenReturn(users);
        Mockito.when(accessKeyDAO.findAll(users[0])).thenReturn(accessKeys);
        Mockito.when(roleDAO.findAll(account, "/")).thenReturn(roles);
        Mockito.doReturn(resp).when(s3).notifyDeleteAccount(
            any(String.class), any(String.class), any(String.class),
            any(String.class));

        final String expectedResponseBody =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" " +
            "standalone=\"no\"?><DeleteAccountResponse " +
            "xmlns=\"https://iam.seagate" +
            ".com/doc/2010-05-08/\"><ResponseMetadata><RequestId>0000</" +
            "RequestId><" + "/ResponseMetadata></DeleteAccountResponse>";

        ServerResponse response = accountController.delete();
        Assert.assertEquals(expectedResponseBody, response.getResponseBody());
        Assert.assertEquals(HttpResponseStatus.OK,
                            response.getResponseStatus());

        Mockito.verify(accessKeyDAO).delete(accessKeys[0]);
        Mockito.verify(userDAO).delete(users[0]);
        Mockito.verify(accountDAO).deleteOu(account, LDAPUtils.USER_OU);
        Mockito.verify(accountDAO).deleteOu(account, LDAPUtils.ROLE_OU);
        Mockito.verify(accountDAO).deleteOu(account, LDAPUtils.GROUP_OU);
        Mockito.verify(accountDAO).deleteOu(account, LDAPUtils.POLICY_OU);
        Mockito.verify(accountDAO).delete(account);
        Mockito.verify(roleDAO).delete(roles[0]);
    }

    @Test public void
    DeleteAccountWithLdapCred_RootUserSearchFailed_ReturnInternalServerError()
        throws DataAccessException {
      Account account = mock(Account.class);

      Mockito.when(account.getName()).thenReturn("s3test");
      Mockito.when(accountDAO.find("s3test")).thenReturn(account);
      Mockito.when(account.exists()).thenReturn(Boolean.TRUE);
      Mockito.when(userDAO.find("s3test", "root")).thenReturn(new User());

      final String expectedResponseBody =
          "<?xml version=\"1.0\" " + "encoding=\"UTF-8\" standalone=\"no\"?>" +
          "<ErrorResponse " +
          "xmlns=\"https://iam.seagate.com/doc/2010-05-08/\">" +
          "<Error><Code>InternalFailure</Code>" +
          "<Message>The request processing has failed because of an " +
          "unknown error, exception or failure.</Message></Error>" +
          "<RequestId>0000</RequestId>" + "</ErrorResponse>";

      ServerResponse response = accountController.delete ();
      Assert.assertEquals(expectedResponseBody, response.getResponseBody());
      Assert.assertEquals(HttpResponseStatus.INTERNAL_SERVER_ERROR,
                          response.getResponseStatus());
    }

    @Test public void
    DeleteAccountWithLdapCred_AccountAccessKeySearchFailed_ResturnInternalServerError()
        throws Exception {
      Account account = mock(Account.class);
      User user = new User();
      user = new User();
      user.setName("root");
      user.setId("abcxyz");
      AccessKey accessKeys = mock(AccessKey.class);

      PowerMockito.doReturn("abcxyz")
          .when(AuthServerConfig.class, "getLdapLoginCN");
      Mockito.when(requestor.getAccesskey()).thenReturn(accessKeys);
      Mockito.when(accessKeys.getId()).thenReturn("abcxyz");

      Mockito.when(account.getName()).thenReturn("s3test");
      Mockito.when(accountDAO.find("s3test")).thenReturn(account);
      Mockito.when(account.exists()).thenReturn(Boolean.TRUE);
      Mockito.when(userDAO.find("s3test", "root")).thenReturn(user);
      Mockito.when(requestor.getId()).thenReturn("abcxyz");
      Mockito.doThrow(new DataAccessException(
                          "Failed to find Access Key for account"))
          .when(accessKeyDAO)
          .findAccountAccessKey(user.getId());

      final String expectedResponseBody =
          "<?xml version=\"1.0\" " + "encoding=\"UTF-8\" standalone=\"no\"?>" +
          "<ErrorResponse " +
          "xmlns=\"https://iam.seagate.com/doc/2010-05-08/\">" +
          "<Error><Code>InternalFailure</Code>" +
          "<Message>The request processing has failed because of an " +
          "unknown error, exception or failure.</Message></Error>" +
          "<RequestId>0000</RequestId>" + "</ErrorResponse>";

      ServerResponse response = accountController.delete ();
      Assert.assertEquals(expectedResponseBody, response.getResponseBody());
      Assert.assertEquals(HttpResponseStatus.INTERNAL_SERVER_ERROR,
                          response.getResponseStatus());
    }

    @Test public void DeleteAccountWithLdapCred_Success() throws Exception {
      Account account = mock(Account.class);

      User[] users = new User[1];
      users[0] = new User();
      users[0].setName("root");
      users[0].setId("abcxyz");

      AccessKey accessKey = mock(AccessKey.class);
      accessKey.setId("akey123");
      accessKey.setSecretKey("skey1234");
      accessKey.setToken("test");
      Role role = mock(Role.class);
      AccessKey[] accessKeys = new AccessKey[1];
      accessKeys[0] = accessKey;
      Role[] roles = new Role[1];
      roles[0] = role;

      PowerMockito.doReturn("abcxyz")
          .when(AuthServerConfig.class, "getLdapLoginCN");
      Mockito.when(requestor.getAccesskey()).thenReturn(accessKey);
      Mockito.when(accessKey.getId()).thenReturn("abcxyz");

      Mockito.when(account.getName()).thenReturn("s3test");
      Mockito.when(accountDAO.find("s3test")).thenReturn(account);
      Mockito.when(account.exists()).thenReturn(Boolean.TRUE);
      Mockito.when(userDAO.find("s3test", "root")).thenReturn(users[0]);
      Mockito.when(requestor.getId()).thenReturn("abcxyz");
      Mockito.doReturn(accessKey).when(accessKeyDAO).findAccountAccessKey(
          users[0].getId());

      ServerResponse resp = new ServerResponse();
      resp.setResponseStatus(HttpResponseStatus.OK);
      Mockito.doReturn(resp).when(s3).notifyDeleteAccount(
          any(String.class), any(String.class), any(String.class),
          any(String.class));
      Mockito.when(userDAO.findAll("s3test", "/")).thenReturn(users);
      Mockito.when(accessKeyDAO.findAll(users[0])).thenReturn(accessKeys);
      Mockito.when(roleDAO.findAll(account, "/")).thenReturn(roles);

      final String expectedResponseBody =
          "<?xml version=\"1.0\" encoding=\"UTF-8\" " +
          "standalone=\"no\"?><DeleteAccountResponse " +
          "xmlns=\"https://iam.seagate" +
          ".com/doc/2010-05-08/\"><ResponseMetadata><RequestId>0000</" +
          "RequestId><" + "/ResponseMetadata></DeleteAccountResponse>";

      ServerResponse response = accountController.delete ();
      Assert.assertEquals(expectedResponseBody, response.getResponseBody());
      Assert.assertEquals(HttpResponseStatus.OK, response.getResponseStatus());
    }

   private
    void mockCreateAccessKey() throws Exception {
      AccessKey mockAccessKey = mockAccessKey("AKIASIAS", "htuspscae/123");
      PowerMockito.doReturn(mockAccessKey)
          .when(AccessKeyService.class, "createAccessKey", any(User.class));
    }

   private
    void mockCreateAccessKeyWithKeys() throws Exception {
      AccessKey mockAccessKey =
          mockAccessKey("AKIASIASCustom", "htuspscae/123/custom");
      PowerMockito.doReturn(mockAccessKey)
          .when(AccessKeyService.class, "createAccessKey", any(User.class),
                any(String.class), any(String.class));
    }

   private
    AccessKey mockAccessKey(String accessKeyId, String secretKey) {
      AccessKey mockAccessKey = mock(AccessKey.class);
      Mockito.when(mockAccessKey.getId()).thenReturn(accessKeyId);
      Mockito.when(mockAccessKey.getSecretKey()).thenReturn(secretKey);
      Mockito.when(mockAccessKey.getStatus()).thenReturn("Active");

      return mockAccessKey;
    }
}

