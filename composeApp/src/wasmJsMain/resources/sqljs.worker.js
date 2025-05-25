import initSqlJs from "sql.js";

let db = null;
const DB_STORAGE_KEY = "kristine-db";

async function createDatabase() {
    let SQL = await initSqlJs({locateFile: file => '/sql-wasm.wasm'});

    // Try to load the database from IndexedDB
    try {
        const savedDbData = await loadFromIndexedDB(DB_STORAGE_KEY);
        if (savedDbData) {
            // Create database from saved data
            db = new SQL.Database(savedDbData);
            console.log("Database loaded from cache");
        } else {
            // Create a new database if no saved data exists
            db = new SQL.Database();
            console.log("New database created");
        }
    } catch (error) {
        console.error("Error loading database from cache:", error);
        db = new SQL.Database();
    }
}

// Save database to IndexedDB
async function saveToIndexedDB(key, data) {
    return new Promise((resolve, reject) => {
        const request = indexedDB.open("KristineDB", 1);

        request.onupgradeneeded = function (event) {
            const db = event.target.result;
            if (!db.objectStoreNames.contains('databases')) {
                db.createObjectStore('databases');
            }
        };

        request.onsuccess = function (event) {
            const db = event.target.result;
            const transaction = db.transaction(['databases'], 'readwrite');
            const store = transaction.objectStore('databases');

            const storeRequest = store.put(data, key);
            storeRequest.onsuccess = () => resolve();
            storeRequest.onerror = () => reject(storeRequest.error);

            transaction.oncomplete = () => db.close();
        };

        request.onerror = function () {
            reject(request.error);
        };
    });
}

// Load database from IndexedDB
async function loadFromIndexedDB(key) {
    return new Promise((resolve, reject) => {
        const request = indexedDB.open("KristineDB", 1);

        request.onupgradeneeded = function (event) {
            const db = event.target.result;
            if (!db.objectStoreNames.contains('databases')) {
                db.createObjectStore('databases');
            }
        };

        request.onsuccess = function (event) {
            const db = event.target.result;
            const transaction = db.transaction(['databases'], 'readonly');
            const store = transaction.objectStore('databases');

            const getRequest = store.get(key);
            getRequest.onsuccess = () => resolve(getRequest.result);
            getRequest.onerror = () => reject(getRequest.error);

            transaction.oncomplete = () => db.close();
        };

        request.onerror = function () {
            reject(request.error);
        };
    });
}

function onModuleReady() {
    const data = this.data;

    switch (data && data.action) {
        case "exec":
            if (!data["sql"]) {
                throw new Error("exec: Missing query string");
            }

            const results = db.exec(data.sql, data.params)[0] ?? {values: []};

            // Save database after each operation
            // You might want to optimize this to save less frequently
            const dbData = db.export();
            saveToIndexedDB(DB_STORAGE_KEY, dbData)
                .catch(err => console.error("Error saving database:", err));

            return postMessage({
                id: data.id,
                results: results
            });
        case "begin_transaction":
            return postMessage({
                id: data.id,
                results: db.exec("BEGIN TRANSACTION;")
            })
        case "end_transaction":
            const transactionResults = db.exec("END TRANSACTION;");

            // Save database after transaction is committed
            const transactionDbData = db.export();
            saveToIndexedDB(DB_STORAGE_KEY, transactionDbData)
                .catch(err => console.error("Error saving database after transaction:", err));

            return postMessage({
                id: data.id,
                results: transactionResults
            })
        case "rollback_transaction":
            return postMessage({
                id: data.id,
                results: db.exec("ROLLBACK TRANSACTION;")
            })
        // Add a new action to explicitly save the database
        case "save_database":
            try {
                const saveData = db.export();
                saveToIndexedDB(DB_STORAGE_KEY, saveData);
                return postMessage({
                    id: data.id,
                    results: {success: true}
                });
            } catch (error) {
                return postMessage({
                    id: data.id,
                    error: error.message
                });
            }
        default:
            throw new Error(`Unsupported action: ${data && data.action}`);
    }
}

function onError(err) {
    return postMessage({
        id: this.data.id,
        error: err
    });
}

if (typeof importScripts === "function") {
    db = null;
    const sqlModuleReady = createDatabase()
    self.onmessage = (event) => {
        return sqlModuleReady
            .then(onModuleReady.bind(event))
            .catch(onError.bind(event));
    }
}
