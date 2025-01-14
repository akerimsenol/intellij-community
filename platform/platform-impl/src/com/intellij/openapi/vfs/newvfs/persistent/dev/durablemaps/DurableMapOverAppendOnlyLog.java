// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev.durablemaps;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.Processor;
import com.intellij.util.io.dev.appendonlylog.AppendOnlyLog;
import com.intellij.util.io.dev.durablemaps.DurableMap;
import com.intellij.util.io.dev.enumerator.DataExternalizerEx;
import com.intellij.util.io.dev.enumerator.DataExternalizerEx.KnownSizeRecordWriter;
import com.intellij.util.io.dev.enumerator.KeyDescriptorEx;
import com.intellij.util.io.dev.intmultimaps.DurableIntToMultiIntMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Simplest implementation: (key, value) pairs stored in append-only log, {@link DurableIntToMultiIntMap} is used to keep
 * and update the mapping.
 * Map doesn't allow null keys. It does allow null values, but {@code .put(key,null)} is equivalent to {@code .remove(key)}
 * Map needs a compaction from time to time
 */
public class DurableMapOverAppendOnlyLog<K, V> implements DurableMap<K, V> {

  private final AppendOnlyLog keyValuesLog;
  private final DurableIntToMultiIntMap keyHashToIdMap;

  private final KeyDescriptorEx<K> keyDescriptor;
  /**
   * We need not just {@link DataExternalizerEx} but {@link KeyDescriptorEx} for values, because we need to compare values
   * to skip storing duplicates
   */
  private final KeyDescriptorEx<V> valueDescriptor;

  public DurableMapOverAppendOnlyLog(@NotNull AppendOnlyLog keyValuesLog,
                                     @NotNull DurableIntToMultiIntMap keyHashToIdMap,
                                     @NotNull KeyDescriptorEx<K> keyDescriptor,
                                     @NotNull KeyDescriptorEx<V> valueDescriptor) {
    this.keyValuesLog = keyValuesLog;
    this.keyHashToIdMap = keyHashToIdMap;

    this.keyDescriptor = keyDescriptor;
    this.valueDescriptor = valueDescriptor;
  }

  @Override
  public boolean containsMapping(@NotNull K key) throws IOException {
    int keyHash = adjustHash(keyDescriptor.getHashCode(key));

    int foundRecordId = keyHashToIdMap.lookup(keyHash, recordId -> {
      long logRecordId = convertStoredIdToLogId(recordId);
      return keyValuesLog.read(logRecordId, recordBuffer -> {
        int keyRecordSize = recordBuffer.getInt(0);
        if (keyRecordSize < 0) {
          return false;//negative key-size => value=null <=> no mapping
        }
        ByteBuffer keyRecordSlice = recordBuffer.slice(Integer.BYTES, keyRecordSize);
        K candidateKey = keyDescriptor.read(keyRecordSlice);
        if (keyDescriptor.isEqual(key, candidateKey)) {
          return true;
        }
        else {
          return false;
        }
      });
    });

    return foundRecordId != DurableIntToMultiIntMap.NO_VALUE;
  }

  @Override
  public V get(@NotNull K key) throws IOException {
    int keyHash = adjustHash(keyDescriptor.getHashCode(key));

    Ref<Pair<K, V>> resultRef = new Ref<>();
    keyHashToIdMap.lookup(keyHash, recordId -> {
      long logRecordId = convertStoredIdToLogId(recordId);
      Pair<K, V> entry = readEntryIfKeyMatch(logRecordId, key);
      if (entry != null) {
        resultRef.set(entry);
        return true;
      }
      else {
        return false;
      }
    });

    Pair<K, V> entry = resultRef.get();
    if (entry == null) {
      return null;
    }
    else {
      return entry.second;
    }
  }

  @Override
  public void put(@NotNull K key,
                  @Nullable V value) throws IOException {
    int keyHash = adjustHash(keyDescriptor.getHashCode(key));
    //Abstraction break: synchronize on keyHashToIdMap because we know keyHashToIdMap uses this-monitor to synchronize
    //    itself
    synchronized (keyHashToIdMap) {
      Ref<Pair<K, V>> resultRef = new Ref<>();
      int foundRecordId = keyHashToIdMap.lookup(
        keyHash,
        candidateRecordId -> {
          long logRecordId = convertStoredIdToLogId(candidateRecordId);
          Pair<K, V> entry = readEntryIfKeyMatch(logRecordId, key);
          if (entry == null) {
            return false; // [record.key != key] => hash collision => look further
          }
          if (nullSafeEquals(value, entry.second)) {
            //record with key existed, and with the same value => just return
            // (don't store entry ref -- we already know both key & value)
            return true;
          }
          //record with key exists, but with different value
          resultRef.set(entry);
          return true;
        }
      );

      boolean keyRecordExists = (foundRecordId != DurableIntToMultiIntMap.NO_VALUE);
      boolean valueIsSame = resultRef.isNull();

      //Check is value differ from current value -- we don't need to append the log with the same (key,value)
      // again and again
      if (keyRecordExists && valueIsSame) {
        return; // [current value == new value] => nothing to do
      }

      long logRecordId = appendEntry(key, value);
      int storedRecordId = convertLogIdToStoredId(logRecordId);

      if (keyRecordExists) {
        // (key) record exist, but with different value => replace recordId:
        if (value != null) {
          keyHashToIdMap.replace(keyHash, foundRecordId, storedRecordId);
        }
        else {//remove deleted mapping
          keyHashToIdMap.remove(keyHash, foundRecordId);
        }
      }
      else {
        // (key) record don't exist yet => put it:
        if (value != null) {
          keyHashToIdMap.put(keyHash, storedRecordId);
        }
      }
    }
  }


  @Override
  public void remove(@NotNull K key) throws IOException {
    put(key, null);
  }

  @Override
  public boolean processKeys(@NotNull Processor<? super K> processor) throws IOException {
    //TODO currently no keyHashToIdMap.forEach() method
    throw new UnsupportedOperationException("Method is not implemented yet");
  }

  public boolean isEmpty() throws IOException {
    return keyHashToIdMap.isEmpty();
  }

  public int size() throws IOException {
    return keyHashToIdMap.size();
  }

  @Override
  public @NotNull CompactionScore compactionScore() throws IOException {
    //TODO return keyHashToIdMap.size() * 1.0/keyValuesLog.size()
    throw new UnsupportedOperationException("Method is not implemented yet");
  }

  @Override
  public @NotNull DurableMap<K, V> compact() throws IOException {
    //TODO please, implement me
    throw new UnsupportedOperationException("Method is not implemented yet");
  }


  @Override
  public void force() throws IOException {
    keyValuesLog.flush();
    keyHashToIdMap.flush();
  }

  @Override
  public boolean isDirty() {
    //as usual, assume mapped-files based impls are never 'dirty':
    return false;
  }


  @Override
  public boolean isClosed() {
    //TODO please, implement me
    throw new UnsupportedOperationException("Method is not implemented yet");
  }

  @Override
  public void close() throws IOException {
    ExceptionUtil.runAllAndRethrowAllExceptions(
      IOException.class,
      () -> new IOException("Can't close " + keyValuesLog + "/" + keyHashToIdMap),
      keyValuesLog::close,
      keyHashToIdMap::close
    );
  }

  @Override
  public void closeAndClean() throws IOException {
    ExceptionUtil.runAllAndRethrowAllExceptions(
      IOException.class,
      () -> new IOException("Can't closeAndClean " + keyValuesLog + "/" + keyHashToIdMap),
      keyValuesLog::closeAndClean,
      keyHashToIdMap::closeAndClean
    );
  }


  // ============================= infrastructure: ============================================================================ //

  private static int convertLogIdToStoredId(long logRecordId) {
    return Math.toIntExact(logRecordId);
  }

  private static long convertStoredIdToLogId(int storedRecordId) {
    return storedRecordId;
  }

  /** @return hash that is acceptable as a key in keyHashToIdMap */
  private static int adjustHash(int hash) {
    if (hash == DurableIntToMultiIntMap.NO_VALUE) {
      //DurableIntToMultiIntMap doesn't allow 0 keys/values, hence replace 0 key with just anything !=0.
      // Key (=hash) doesn't identify value uniquely anyway, hence this replacement just adds another
      // collision -- basically, we replaced original Key.hash with our own hash, which avoids 0 at
      // the cost of slightly higher collision chances
      return -1;// anything !=0 will do
    }
    return hash;
  }

  /** valueDescriptor is expected to NOT process null values, so we compare null values separately */
  private boolean nullSafeEquals(@Nullable V value,
                                 @Nullable V anotherValue) {
    if ((anotherValue == null && value == null)) {
      return true;
    }
    if ((anotherValue != null && value != null) && valueDescriptor.isEqual(value, anotherValue)) {
      return true;
    }
    return false;
  }

  /** @return [key, value] pair by logRecordId, if key==expectedKey, null if the record contains key!=expectedKey */
  private Pair<K, V> readEntryIfKeyMatch(long logRecordId,
                                         @NotNull K expectedKey) throws IOException {
    return keyValuesLog.read(logRecordId, recordBuffer -> {
      int header = recordBuffer.getInt(0);
      int keyRecordSize = Math.abs(header);
      boolean valueIsNull = header < 0;

      ByteBuffer keyRecordSlice = recordBuffer.slice(Integer.BYTES, keyRecordSize);
      K candidateKey = keyDescriptor.read(keyRecordSlice);
      if (!keyDescriptor.isEqual(expectedKey, candidateKey)) {
        return null;
      }

      if (valueIsNull) {
        return Pair.pair(expectedKey, null);
      }

      int valueLength = recordBuffer.remaining() - keyRecordSize - Integer.BYTES;
      ByteBuffer valueRecordSlice = recordBuffer.slice(Integer.BYTES + keyRecordSize, valueLength);
      V candidateValue = valueDescriptor.read(valueRecordSlice);
      return Pair.pair(expectedKey, candidateValue);
    });
  }

  private long appendEntry(@NotNull K key,
                           @Nullable V value) throws IOException {
    KnownSizeRecordWriter keyWriter = keyDescriptor.writerFor(key);
    int keySize = keyWriter.recordSize();

    if (value == null) {
      int recordSize = Integer.BYTES + keySize;
      return keyValuesLog.append(buffer -> {
        buffer.putInt(0, -keySize);//negative keySize -- marks of [value=null]
        keyWriter.write(buffer.slice(Integer.BYTES, keySize));
        buffer.position(recordSize);
        return buffer;
      }, recordSize);
    }
    else {
      KnownSizeRecordWriter valueWriter = valueDescriptor.writerFor(value);
      int valueSize = valueWriter.recordSize();
      int recordSize = Integer.BYTES + keySize + valueSize;
      return keyValuesLog.append(buffer -> {
        //MAYBE RC: use varint DataInputOutputUtil.writeINT(buffer, keySize) -- but this makes record size computation more difficult
        buffer.putInt(0, keySize);
        keyWriter.write(buffer.slice(Integer.BYTES, keySize));
        valueWriter.write(buffer.slice(Integer.BYTES + keySize, valueSize));
        buffer.position(recordSize);
        return buffer;
      }, recordSize);
    }
  }
}
