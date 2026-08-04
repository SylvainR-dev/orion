import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';

import { PersonService, Person } from './person.service';
import { Organization } from './organization.service';
import { API_BASE_URL } from './config';

describe('PersonService', () => {
  let service: PersonService;
  let httpMock: HttpTestingController;

  const makePerson = (overrides: Partial<Person> = {}): Person => ({
    id: 1,
    firstName: 'Jane',
    lastName: 'Doe',
    email: 'jane@doe.com',
    phone: '0600000000',
    bio: 'bio',
    createdAt: new Date(),
    organizations: [],
    ...overrides,
  });

  const makeOrg = (overrides: Partial<Organization> = {}): Organization => ({
    id: 10,
    name: 'Orion',
    createdAt: new Date(),
    personIds: [],
    persons: [],
    ...overrides,
  });

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule]
    });
    service = TestBed.inject(PersonService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('fetchAll should GET /api/persons and return the list as-is', async () => {
    const persons = [makePerson({ id: 1 }), makePerson({ id: 2 })];

    const promise = service.fetchAll();

    const req = httpMock.expectOne(`${API_BASE_URL}/api/persons`);
    expect(req.request.method).toBe('GET');
    req.flush(persons);

    expect(await promise).toEqual(persons);
  });

  it('fetchPersonOrganizations should keep only organizations containing the personId', async () => {
    const orgs = [
      makeOrg({ id: 10, personIds: [1, 2] }),
      makeOrg({ id: 20, personIds: [2, 3] }),
    ];

    const promise = service.fetchPersonOrganizations(1);

    const req = httpMock.expectOne(`${API_BASE_URL}/api/organizations`);
    expect(req.request.method).toBe('GET');
    req.flush(orgs);

    const result = await promise;
    expect(result.length).toBe(1);
    expect(result[0].id).toBe(10);
    expect(result[0].persons).toEqual([]);
  });

  it('fetchById should GET the person then hydrate organizations', fakeAsync(() => {
    const person = makePerson({ id: 1 });
    const orgs = [makeOrg({ id: 10, personIds: [1] })];
    let result!: Person;

    service.fetchById(1).then(r => result = r);

    const personReq = httpMock.expectOne(`${API_BASE_URL}/api/persons/1`);
    expect(personReq.request.method).toBe('GET');
    personReq.flush(person);
    tick();

    const orgsReq = httpMock.expectOne(`${API_BASE_URL}/api/organizations`);
    orgsReq.flush(orgs);
    tick();

    expect(result.organizations.length).toBe(1);
    expect(result.organizations[0].id).toBe(10);
  }));

  it('deleteById should DELETE /api/persons/:id', async () => {
    const promise = service.deleteById(1);

    const req = httpMock.expectOne(`${API_BASE_URL}/api/persons/1`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);

    await expectAsync(promise).toBeResolved();
  });

  it('save should POST when the person has no id, then hydrate organizations', fakeAsync(() => {
    const newPerson = makePerson({ id: undefined as unknown as number });
    const created = makePerson({ id: 5 });
    let result!: Person;

    service.save(newPerson).then(r => result = r);

    const postReq = httpMock.expectOne(`${API_BASE_URL}/api/persons`);
    expect(postReq.request.method).toBe('POST');
    expect(postReq.request.body).toEqual({
      firstName: newPerson.firstName,
      lastName: newPerson.lastName,
      bio: newPerson.bio,
      phone: newPerson.phone,
      email: newPerson.email,
    });
    postReq.flush(created);
    tick();

    const orgsReq = httpMock.expectOne(`${API_BASE_URL}/api/organizations`);
    orgsReq.flush([]);
    tick();

    expect(result.id).toBe(5);
    expect(result.organizations).toEqual([]);
  }));

  it('save should PUT when the person already has an id', fakeAsync(() => {
    const existing = makePerson({ id: 7 });

    service.save(existing).then();

    const putReq = httpMock.expectOne(`${API_BASE_URL}/api/persons/7`);
    expect(putReq.request.method).toBe('PUT');
    putReq.flush(existing);
    tick();

    const orgsReq = httpMock.expectOne(`${API_BASE_URL}/api/organizations`);
    orgsReq.flush([]);
    tick();
  }));
});