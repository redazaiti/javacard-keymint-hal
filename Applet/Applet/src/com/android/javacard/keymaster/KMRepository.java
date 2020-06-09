/*
 * Copyright(C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.javacard.keymaster;

import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.JCSystem;
import javacard.framework.Util;

public class KMRepository {
  //TODO make the sizes configurable
  public static final short HEAP_SIZE = 0x2000;
  public static final short MAX_BLOB_STORAGE = 32;
  public static final short AES_GCM_AUTH_TAG_LENGTH = 12;
  public static final short HMAC_SEED_NONCE_SIZE = 16;
  public static final short MAX_OPS = 4;
  public static final short COMPUTED_HMAC_KEY_SIZE = 32;
  // Boot params constants
  public static final byte BOOT_KEY_MAX_SIZE = 32;
  public static final byte BOOT_HASH_MAX_SIZE = 32;
  // Repository attributes
  private static KMRepository repository;
  private byte[] masterKey;
  private byte[] hmacSeed;
  private byte[] hmacKey;
  private byte[] computedHmacKey;
  private byte[] hmacNonce;
  private byte[] heap;
  private Object[] operationStateTable;
  private short heapIndex;
  // boot parameters
  public Object[] authTagRepo;
  public short keyBlobCount;
  public byte[] osVersion;
  public byte[] osPatch;
  public byte[] verifiedBootKey;
  public short actualBootKeyLength;
  public byte[] verifiedBootHash;
  public short actualBootHashLength;
  public boolean verifiedBootFlag;
  public boolean selfSignedBootFlag;
  public boolean deviceLockedFlag;

  public static KMRepository instance() {
    return repository;
  }

  public KMRepository() {
    heap = JCSystem.makeTransientByteArray(HEAP_SIZE, JCSystem.CLEAR_ON_RESET);
    authTagRepo = new Object[MAX_BLOB_STORAGE];
    short index = 0;
    while (index < MAX_BLOB_STORAGE) {
      authTagRepo[index] = new KMAuthTag();
      ((KMAuthTag) authTagRepo[index]).reserved = false;
      ((KMAuthTag) authTagRepo[index]).authTag = new byte[AES_GCM_AUTH_TAG_LENGTH];
      ((KMAuthTag) authTagRepo[index]).usageCount = 0;
      index++;
    }
    osVersion = new byte[4];
    osPatch = new byte[4];
    verifiedBootKey = new byte[BOOT_KEY_MAX_SIZE];
    verifiedBootHash = new byte[BOOT_HASH_MAX_SIZE];
    operationStateTable = new Object[MAX_OPS];
    index = 0;
    while(index < MAX_OPS){
      operationStateTable[index] = new KMOperationState();
      ((KMOperationState)operationStateTable[index]).reset();
      index++;
    }
    repository = this;
  }

  public KMOperationState reserveOperation(){
    short index = 0;
    while(index < MAX_OPS){
      if(!((KMOperationState)operationStateTable[index]).isActive()){
        ((KMOperationState)operationStateTable[index]).activate();
        return (KMOperationState)operationStateTable[index];
      }
      index++;
    }
    return null;
  }
  public void releaseOperation(KMOperationState op){
    op.reset();
  }
  public void initMasterKey(byte[] key, short len) {
    if (masterKey == null) {
      masterKey = new byte[len];
      Util.arrayCopy(key, (short) 0, masterKey, (short) 0, len);
    }
  }

  public void initHmacKey(byte[] key, short len) {
    if (hmacKey == null) {
      hmacKey = new byte[len];
      Util.arrayCopy(key, (short) 0, hmacKey, (short) 0, len);
    }
  }

  public void initHmacSeed(byte[] seed, short len) {
    if (hmacSeed == null) {
      hmacSeed = new byte[len];
      Util.arrayCopy(seed, (short) 0, hmacSeed, (short) 0, len);
    }
  }

  public void initComputedHmac(byte[] key, short start, short len) {
    if (computedHmacKey == null) {
      computedHmacKey = new byte[len];
      Util.arrayCopy(key, (short) 0, computedHmacKey, start, len);
    }
  }

  public void initHmacNonce(byte[] nonce, short offset, short len) {
    if (hmacNonce == null) {
      hmacNonce = new byte[len];
    } else if (len != hmacNonce.length) {
      KMException.throwIt(KMError.INVALID_INPUT_LENGTH);
    }
    Util.arrayCopy(nonce, (short) 0, hmacSeed, (short) 0, len);
  }

  public void onUninstall() {
    // TODO change this
    Util.arrayFillNonAtomic(masterKey, (short) 0, (short) masterKey.length, (byte) 0);
  }

  public void onProcess() {}

  public void clean() {
    Util.arrayFillNonAtomic(heap, (short) 0, heapIndex, (byte) 0);
    heapIndex = 0;
  }

  public void onDeselect() {}

  public void onSelect() {}

  public byte[] getMasterKeySecret() {
    return masterKey;
  }

  public short alloc(short length) {
    if (((short) (heapIndex + length)) > heap.length) {
      ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
    }
    heapIndex += length;
    return (short) (heapIndex - length);
  }

  public byte[] getHeap() {
    return heap;
  }

  public byte[] getHmacSeed() {
    return hmacSeed;
  }

  public byte[] getHmacKey() {
    return hmacKey;
  }

  public byte[] getHmacNonce() {
    return hmacNonce;
  }

  public void setHmacNonce(byte[] hmacNonce) {
    Util.arrayCopy(hmacNonce, (short) 0, this.hmacNonce, (short) 0, HMAC_SEED_NONCE_SIZE);
  }
  public byte[] getComputedHmacKey() {
    return computedHmacKey;
  }

  public void setComputedHmacKey(byte[] computedHmacKey) {
    Util.arrayCopy( computedHmacKey, (short) 0, this.computedHmacKey, (short) 0, COMPUTED_HMAC_KEY_SIZE);
  }

  public void persistAuthTag(short authTag) {
    short index = 0;
    while (index < MAX_BLOB_STORAGE) {
      if (!((KMAuthTag) authTagRepo[index]).reserved) {
        JCSystem.beginTransaction();
        ((KMAuthTag) authTagRepo[index]).reserved = true;
        Util.arrayCopy(
            KMByteBlob.cast(authTag).getBuffer(),
            KMByteBlob.cast(authTag).getStartOff(),
            ((KMAuthTag) authTagRepo[index]).authTag ,
            (short) 0,
            AES_GCM_AUTH_TAG_LENGTH);
        keyBlobCount++;
        JCSystem.commitTransaction();
        break;
      }
      index++;
    }
  }

  public boolean validateAuthTag(short authTag) {
    KMAuthTag tag = findTag(authTag);
    if(tag != null){
      return true;
    }
    return false;
  }

  public void removeAuthTag(short authTag) {
    KMAuthTag tag = findTag(authTag);
    if(tag == null){
      ISOException.throwIt(ISO7816.SW_COMMAND_NOT_ALLOWED);
    }
    JCSystem.beginTransaction();
    tag.reserved = false;
    Util.arrayFill(tag.authTag, (short) 0, AES_GCM_AUTH_TAG_LENGTH, (byte) 0);
    tag.usageCount = 0;
    keyBlobCount--;
    JCSystem.commitTransaction();
  }

  public void removeAllAuthTags() {
    JCSystem.beginTransaction();
    short index = 0;
    while (index < MAX_BLOB_STORAGE) {
      ((KMAuthTag) authTagRepo[index]).reserved = false;
      Util.arrayFill(
          ((KMAuthTag) authTagRepo[index]).authTag, (short) 0, AES_GCM_AUTH_TAG_LENGTH, (byte) 0);
      ((KMAuthTag) authTagRepo[index]).usageCount = 0;
      index++;
    }
    keyBlobCount = 0;
    JCSystem.commitTransaction();
  }

  public KMAuthTag findTag(short authTag) {
    short index = 0;
    short found = 0;
    while (index < MAX_BLOB_STORAGE) {
      if (((KMAuthTag) authTagRepo[index]).reserved) {
        found =
            Util.arrayCompare(
                ((KMAuthTag) authTagRepo[index]).authTag,
                (short) 0,
                KMByteBlob.cast(authTag).getBuffer(),
                KMByteBlob.cast(authTag).getStartOff(),
                AES_GCM_AUTH_TAG_LENGTH);
        if (found == 0) {
          return (KMAuthTag) authTagRepo[index];
        }
      }
      index++;
    }
    return null;
  }

  public short getRateLimitedKeyCount(short authTag) {
    KMAuthTag tag = findTag(authTag);
    if (tag != null) {
      return tag.usageCount;
    }
    return KMType.INVALID_VALUE;
  }

  public void setRateLimitedKeyCount(short authTag, short val) {
    KMAuthTag tag = findTag(authTag);
    JCSystem.beginTransaction();
    if (tag != null) {
      tag.usageCount = val;
    }
    JCSystem.commitTransaction();
  }

  public KMOperationState findOperation(short opHandle) {
    short index = 0;
    while(index < MAX_OPS){
      if(((KMOperationState)operationStateTable[index]).isActive() &&
        ((KMOperationState)operationStateTable[index]).getHandle() == opHandle){
        return (KMOperationState)operationStateTable[index];
      }
      index++;
    }
    return null;
  }
}
